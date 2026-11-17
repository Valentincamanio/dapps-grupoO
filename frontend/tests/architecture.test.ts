// Reglas de arquitectura del frontend (F1 a F10.2, contracts/architecture-rules.md de la feature 010).
// Lee src/ con import.meta.glob, lo parsea con el compilador de TypeScript y aplica analyze, una función
// pura que se usa igual con el código real y con fuentes armadas en memoria para los casos borde.
import { describe, expect, it } from 'vitest';
import * as ts from 'typescript';

// Ruta relativa a frontend/ -> código fuente ('' para los tests, que solo cuentan por su nombre)
type Sources = Record<string, string>;

interface RuleInfo {
  id: string;
  name: string;
  because: string;
}

// Regla de dependencias entre carpetas: mayUse es una lista blanca y mayNotUse, una lista negra. Los
// nombres son grupos (carpetas de primer nivel de src/ o archivos de la raíz) o 'package:<nombre>'.
interface DependencyRule extends RuleInfo {
  appliesTo: string[] | { except: string[] };
  mayUse?: string[];
  mayNotUse?: string[];
}

interface Violation {
  ruleId: string;
  file: string;
  message: string;
}

// ---------------------------------------------------------------------------------------------------
// Lectura de las fuentes
// ---------------------------------------------------------------------------------------------------

// El código de src/ como texto. Los dos globs sin eager dan solo los nombres: esos tests no se cargan.
const SRC_CODE = import.meta.glob<string>('../src/**/*.{ts,tsx}', {
  query: '?raw',
  import: 'default',
  eager: true,
});
const SRC_TESTS = import.meta.glob('../src/**/*.{test,spec}.*');
const MIRRORED_TESTS = import.meta.glob('./{components,hooks,services,utils}/**/*.test.{ts,tsx}');

// '../src/pages/BoardPage.tsx' -> 'src/pages/BoardPage.tsx'; './utils/format.test.ts' -> 'tests/utils/format.test.ts'
function toFrontendPath(key: string): string {
  return key.startsWith('../') ? key.slice('../'.length) : `tests/${key.slice('./'.length)}`;
}

function readSources(): Sources {
  const sources: Sources = {};
  for (const [key, code] of Object.entries(SRC_CODE)) {
    sources[toFrontendPath(key)] = code;
  }
  for (const key of [...Object.keys(SRC_TESTS), ...Object.keys(MIRRORED_TESTS)]) {
    sources[toFrontendPath(key)] ??= '';
  }
  return sources;
}

// ---------------------------------------------------------------------------------------------------
// Parseo y clasificación de los imports
// ---------------------------------------------------------------------------------------------------

// A dónde apunta un import: un archivo de src/ (con su grupo), un paquete o algo fuera de src/
type Target =
  | { kind: 'source'; specifier: string; path: string; group: string }
  | { kind: 'package'; specifier: string; name: string }
  | { kind: 'outside'; specifier: string };

interface ParsedFile {
  path: string;
  group: string;
  targets: Target[];
  globalUses: string[];
}

const TEST_FILE = /\.(test|spec)\./;

// Los tests de src/ no cuentan para F1 a F9 (FR-006): de ellos se ocupa F10.1
function isProductionFile(path: string): boolean {
  return path.startsWith('src/') && !TEST_FILE.test(path);
}

// Carpeta de primer nivel de src/ ('components', 'pages', ...) o el nombre del archivo si está en la raíz
function groupOf(path: string): string {
  return path.slice('src/'.length).split('/')[0];
}

// Normaliza un import relativo contra la carpeta del archivo que lo hace
function resolveRelative(file: string, specifier: string): string {
  const segments = file.split('/').slice(0, -1);
  for (const part of specifier.split('/')) {
    if (part === '..') {
      segments.pop();
    } else if (part !== '.' && part !== '') {
      segments.push(part);
    }
  }
  return segments.join('/');
}

// '@scope/nombre/subruta' -> '@scope/nombre'; 'react-dom/client' -> 'react-dom'
function packageName(specifier: string): string {
  const parts = specifier.split('/');
  return specifier.startsWith('@') ? parts.slice(0, 2).join('/') : parts[0];
}

// undefined para los .css, que no son dependencias de código
function classify(file: string, specifier: string): Target | undefined {
  if (specifier.split('?')[0].endsWith('.css')) return undefined;
  if (specifier.startsWith('.')) {
    const path = resolveRelative(file, specifier);
    return path.startsWith('src/')
      ? { kind: 'source', specifier, path, group: groupOf(path) }
      : { kind: 'outside', specifier };
  }
  if (specifier.startsWith('/')) return { kind: 'outside', specifier };
  return { kind: 'package', specifier, name: packageName(specifier) };
}

// import (también import type), export ... from e import('...') con un literal, en código o en un tipo
function importSpecifiers(sourceFile: ts.SourceFile): string[] {
  const specifiers: string[] = [];
  const visit = (node: ts.Node): void => {
    if (
      (ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) &&
      node.moduleSpecifier &&
      ts.isStringLiteral(node.moduleSpecifier)
    ) {
      specifiers.push(node.moduleSpecifier.text);
    } else if (
      ts.isCallExpression(node) &&
      node.expression.kind === ts.SyntaxKind.ImportKeyword &&
      node.arguments.length > 0 &&
      ts.isStringLiteralLike(node.arguments[0])
    ) {
      specifiers.push(node.arguments[0].text);
    } else if (
      ts.isImportTypeNode(node) &&
      ts.isLiteralTypeNode(node.argument) &&
      ts.isStringLiteral(node.argument.literal)
    ) {
      specifiers.push(node.argument.literal.text);
    }
    ts.forEachChild(node, visit);
  };
  visit(sourceFile);
  return specifiers;
}

function parse(path: string, code: string): ParsedFile {
  const kind = path.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS;
  const sourceFile = ts.createSourceFile(path, code, ts.ScriptTarget.Latest, true, kind);
  const targets = importSpecifiers(sourceFile)
    .map((specifier) => classify(path, specifier))
    .filter((target): target is Target => target !== undefined);
  return { path, group: groupOf(path), targets, globalUses: globalUses(sourceFile) };
}

// ---------------------------------------------------------------------------------------------------
// Las reglas (identificador, nombre y porqué tal cual el contrato, sección 3)
// ---------------------------------------------------------------------------------------------------

const DEPENDENCY_RULES: DependencyRule[] = [
  {
    id: 'F1',
    name: 'Componentes sin API ni router',
    appliesTo: ['components'],
    mayUse: ['components', 'types', 'utils', 'package:react'],
    because:
      'los componentes reciben datos y callbacks por props; no conocen la API, el router, los hooks ni los contextos',
  },
  {
    id: 'F2',
    name: 'utils puro',
    appliesTo: ['utils'],
    mayUse: ['utils', 'types'],
    because: 'utils son funciones puras: no usan React, otros paquetes ni services',
  },
  {
    id: 'F3',
    name: 'types',
    appliesTo: ['types'],
    mayUse: ['types'],
    because: 'types tiene solo los tipos de la API y del dominio del front',
  },
  {
    id: 'F4',
    name: 'services',
    appliesTo: ['services'],
    mayUse: ['services', 'types'],
    because: 'services es el cliente HTTP y una función por endpoint; no conoce React ni la interfaz',
  },
  {
    id: 'F5',
    name: 'hooks y context',
    appliesTo: ['hooks', 'context'],
    mayNotUse: ['components', 'pages', 'router'],
    because:
      'la lógica con estado vive en hooks y context, que no conocen los componentes, las pantallas ni las rutas',
  },
  {
    id: 'F6.1',
    name: 'Quién usa pages',
    appliesTo: { except: ['router', 'pages'] },
    mayNotUse: ['pages'],
    because: 'hay una pantalla por ruta: las pantallas las usa el router',
  },
  {
    id: 'F6.2',
    name: 'Quién usa router',
    appliesTo: { except: ['main.tsx', 'router'] },
    mayNotUse: ['router'],
    because: 'la definición de rutas la usa solo main.tsx',
  },
];

const NETWORK_RULE: RuleInfo = {
  id: 'F7',
  name: 'Red y almacenamiento',
  because:
    'los componentes y las páginas nunca llaman a fetch directo, y la sesión se guarda solo desde services',
};

const PACKAGES_RULE: RuleInfo = {
  id: 'F8.1',
  name: 'Paquetes permitidos',
  because: 'sin frameworks de UI ni paquetes fuera del stack',
};

const OUTSIDE_RULE: RuleInfo = {
  id: 'F8.2',
  name: 'Nada de afuera',
  because: 'mockups son diseños de referencia y no se importan desde src',
};

const CYCLES_RULE: RuleInfo = {
  id: 'F9',
  name: 'Sin ciclos',
  because: 'un ciclo ata dos archivos que tienen que poder entenderse y probarse por separado',
};

const TESTS_IN_SRC_RULE: RuleInfo = {
  id: 'F10.1',
  name: 'Tests fuera de src',
  because: 'los tests viven solo en frontend/tests/, nunca junto al código',
};

const MIRRORED_TESTS_RULE: RuleInfo = {
  id: 'F10.2',
  name: 'Correspondencia de tests',
  because:
    'tests/ replica la estructura de src/: el test de src/<carpeta>/X.tsx está en tests/<carpeta>/X.test.tsx',
};

const ALL_RULES: RuleInfo[] = [
  ...DEPENDENCY_RULES,
  NETWORK_RULE,
  PACKAGES_RULE,
  OUTSIDE_RULE,
  CYCLES_RULE,
  TESTS_IN_SRC_RULE,
  MIRRORED_TESTS_RULE,
];

function messageOf(rule: RuleInfo, subject: string): string {
  return `[${rule.id}] ${subject}: ${rule.because} (Constitución, sección Frontend)`;
}

function violationOf(rule: RuleInfo, file: string, subject: string): Violation {
  return { ruleId: rule.id, file, message: messageOf(rule, subject) };
}

// ---------------------------------------------------------------------------------------------------
// F1 a F6.2: dependencias entre carpetas
// ---------------------------------------------------------------------------------------------------

function appliesTo(rule: DependencyRule, group: string): boolean {
  return Array.isArray(rule.appliesTo)
    ? rule.appliesTo.includes(group)
    : !rule.appliesTo.except.includes(group);
}

// Lo que el import usa, en los términos de la tabla; undefined si apunta fuera de src/
function usedName(target: Target): string | undefined {
  if (target.kind === 'source') return target.group;
  if (target.kind === 'package') return `package:${target.name}`;
  return undefined;
}

// El propio grupo siempre está permitido en una lista blanca
function breaks(rule: DependencyRule, group: string, target: Target): boolean {
  const used = usedName(target);
  if (rule.mayUse) {
    return used === undefined || (used !== group && !rule.mayUse.includes(used));
  }
  return used !== undefined && (rule.mayNotUse ?? []).includes(used);
}

function checkDependencies(files: ParsedFile[]): Violation[] {
  return DEPENDENCY_RULES.flatMap((rule) =>
    files
      .filter((file) => appliesTo(rule, file.group))
      .flatMap((file) =>
        file.targets
          .filter((target) => breaks(rule, file.group, target))
          .map((target) => violationOf(rule, file.path, `${file.path} usa ${target.specifier}`)),
      ),
  );
}

// ---------------------------------------------------------------------------------------------------
// F7: red y almacenamiento
// ---------------------------------------------------------------------------------------------------

const GLOBAL_APIS = new Set(['fetch', 'sessionStorage', 'localStorage']);
const GLOBAL_OBJECTS = new Set(['window', 'globalThis', 'self']);

// Un identificador suelto usado como valor. No cuentan el nombre de una propiedad (config.fetch) ni el de
// una declaración ({ fetch: ... }, const fetch, fetch() {}, un import); { fetch } sí usa la variable.
function isValueReference(node: ts.Identifier): boolean {
  const parent = node.parent;
  if (ts.isShorthandPropertyAssignment(parent)) return true;
  if ('name' in parent && parent.name === node) return false;
  if ('propertyName' in parent && parent.propertyName === node) return false;
  return !('label' in parent && parent.label === node);
}

// window.fetch, globalThis['localStorage'], self.sessionStorage
function globalMember(node: ts.Node): string | undefined {
  const isGlobalObject = (expression: ts.Expression) =>
    ts.isIdentifier(expression) && GLOBAL_OBJECTS.has(expression.text);
  if (ts.isPropertyAccessExpression(node) && isGlobalObject(node.expression)) {
    return GLOBAL_APIS.has(node.name.text) ? node.name.text : undefined;
  }
  if (
    ts.isElementAccessExpression(node) &&
    isGlobalObject(node.expression) &&
    ts.isStringLiteralLike(node.argumentExpression)
  ) {
    return GLOBAL_APIS.has(node.argumentExpression.text) ? node.argumentExpression.text : undefined;
  }
  return undefined;
}

// Los comentarios no son nodos del AST; los tipos y los imports no usan valores
function globalUses(sourceFile: ts.SourceFile): string[] {
  const uses: string[] = [];
  const visit = (node: ts.Node): void => {
    if (
      ts.isTypeNode(node) ||
      ts.isInterfaceDeclaration(node) ||
      ts.isTypeAliasDeclaration(node) ||
      ts.isImportDeclaration(node)
    ) {
      return;
    }
    if (ts.isIdentifier(node) && GLOBAL_APIS.has(node.text) && isValueReference(node)) {
      uses.push(node.text);
    }
    const member = globalMember(node);
    if (member !== undefined) uses.push(member);
    ts.forEachChild(node, visit);
  };
  visit(sourceFile);
  return uses;
}

function checkNetworkAndStorage(files: ParsedFile[]): Violation[] {
  return files
    .filter((file) => file.group !== 'services')
    .flatMap((file) =>
      file.globalUses.map((name) => violationOf(NETWORK_RULE, file.path, `${file.path} usa ${name}`)),
    );
}

// ---------------------------------------------------------------------------------------------------
// F8.1 y F8.2: paquetes e imports de afuera
// ---------------------------------------------------------------------------------------------------

// Se compara el nombre del paquete, no el prefijo: react-dom/client pasa y react-router-dom no
const ALLOWED_PACKAGES = ['react', 'react-dom', 'react-router'];

function checkPackages(files: ParsedFile[]): Violation[] {
  return files.flatMap((file) =>
    file.targets
      .filter((target) => target.kind === 'package' && !ALLOWED_PACKAGES.includes(target.name))
      .map((target) => violationOf(PACKAGES_RULE, file.path, `${file.path} usa ${target.specifier}`)),
  );
}

function checkOutsideImports(files: ParsedFile[]): Violation[] {
  return files.flatMap((file) =>
    file.targets
      .filter((target) => target.kind === 'outside')
      .map((target) => violationOf(OUTSIDE_RULE, file.path, `${file.path} usa ${target.specifier}`)),
  );
}

// ---------------------------------------------------------------------------------------------------
// F9: ciclos
// ---------------------------------------------------------------------------------------------------

// El archivo al que resuelve un import relativo, si está entre los analizados
function resolveFile(path: string, files: Set<string>): string | undefined {
  return [path, `${path}.ts`, `${path}.tsx`, `${path}/index.ts`, `${path}/index.tsx`].find((candidate) =>
    files.has(candidate),
  );
}

// Lo rota para que empiece por su archivo menor: así el mismo ciclo se escribe siempre igual
function rotateToSmallest(cycle: string[]): string[] {
  const start = cycle.indexOf([...cycle].sort()[0]);
  return [...cycle.slice(start), ...cycle.slice(0, start)];
}

// DFS con colores: sin color, 'visiting' (en la pila) y 'done'. Una arista a un archivo en la pila cierra un ciclo.
function findCycles(graph: Map<string, string[]>): string[][] {
  const color = new Map<string, 'visiting' | 'done'>();
  const stack: string[] = [];
  const cycles = new Map<string, string[]>();
  const visit = (file: string): void => {
    color.set(file, 'visiting');
    stack.push(file);
    for (const next of graph.get(file) ?? []) {
      if (color.get(next) === 'visiting') {
        const cycle = rotateToSmallest(stack.slice(stack.indexOf(next)));
        cycles.set(cycle.join(' -> '), [...cycle, cycle[0]]);
      } else if (!color.has(next)) {
        visit(next);
      }
    }
    stack.pop();
    color.set(file, 'done');
  };
  for (const file of [...graph.keys()].sort()) {
    if (!color.has(file)) visit(file);
  }
  return [...cycles.values()];
}

function checkCycles(files: ParsedFile[]): Violation[] {
  const paths = new Set(files.map((file) => file.path));
  const graph = new Map(
    files.map((file) => {
      const imported = file.targets
        .map((target) => (target.kind === 'source' ? resolveFile(target.path, paths) : undefined))
        .filter((path): path is string => path !== undefined);
      return [file.path, [...new Set(imported)].sort()];
    }),
  );
  return findCycles(graph).map((cycle) => violationOf(CYCLES_RULE, cycle[0], `ciclo ${cycle.join(' -> ')}`));
}

// ---------------------------------------------------------------------------------------------------
// F10.1 y F10.2: dónde viven los tests
// ---------------------------------------------------------------------------------------------------

function checkTestsInSrc(paths: string[]): Violation[] {
  return paths
    .filter((path) => path.startsWith('src/') && TEST_FILE.test(path))
    .map((path) => violationOf(TESTS_IN_SRC_RULE, path, `${path} es un test dentro de src/`));
}

// tests/pages/, tests/mocks/ y tests/setup.ts quedan afuera
const MIRRORED_TEST = /^tests\/(components|hooks|services|utils)\/(.+)\.test\.tsx?$/;

function checkMirroredTests(paths: string[]): Violation[] {
  const existing = new Set(paths);
  return paths.flatMap((path) => {
    const match = MIRRORED_TEST.exec(path);
    if (!match) return [];
    const source = `src/${match[1]}/${match[2]}`;
    return existing.has(`${source}.ts`) || existing.has(`${source}.tsx`)
      ? []
      : [violationOf(MIRRORED_TESTS_RULE, path, `${path} no tiene ${source}.ts ni .tsx`)];
  });
}

// ---------------------------------------------------------------------------------------------------
// Análisis
// ---------------------------------------------------------------------------------------------------

// Todas las violaciones de todas las reglas; F1 a F9 miran solo los archivos de producción
function analyze(sources: Sources): Violation[] {
  const paths = Object.keys(sources).sort();
  const files = paths.filter(isProductionFile).map((path) => parse(path, sources[path]));
  return [
    ...checkDependencies(files),
    ...checkNetworkAndStorage(files),
    ...checkPackages(files),
    ...checkOutsideImports(files),
    ...checkCycles(files),
    ...checkTestsInSrc(paths),
    ...checkMirroredTests(paths),
  ];
}

// Los mensajes de una sola regla, para que cada una se informe por separado
function messagesOf(violations: Violation[], ruleId: string): string[] {
  return violations.filter((violation) => violation.ruleId === ruleId).map((violation) => violation.message);
}

function expectedMessage(ruleId: string, subject: string): string {
  const rule = ALL_RULES.find((candidate) => candidate.id === ruleId);
  if (!rule) throw new Error(`No hay una regla ${ruleId}`);
  return messageOf(rule, subject);
}

// ---------------------------------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------------------------------

describe('código real', () => {
  it('el análisis lee todo src/ y los tests que revisa F10.2', () => {
    const sources = readSources();
    const paths = Object.keys(sources);
    const missingFolders = (root: string, folders: string[]) =>
      folders.filter((folder) => !paths.some((path) => path.startsWith(`${root}/${folder}/`)));

    const missingInSrc = missingFolders('src', [
      'components',
      'context',
      'hooks',
      'pages',
      'router',
      'services',
      'types',
      'utils',
    ]);
    const missingInTests = missingFolders('tests', ['components', 'hooks', 'services', 'utils']);

    expect(sources['src/main.tsx']).toContain('import');
    expect(missingInSrc).toEqual([]);
    expect(missingInTests).toEqual([]);
  });

  for (const rule of ALL_RULES) {
    it(`${rule.id} ${rule.name}: el código de src/ la cumple`, () => {
      const sources = readSources();

      const violations = analyze(sources);

      expect(messagesOf(violations, rule.id)).toEqual([]);
    });
  }
});

describe('casos borde', () => {
  it('F1 detecta un componente que usa services', () => {
    const sources: Sources = {
      'src/components/Card.tsx': "import { request } from '../services/httpClient';\nexport const load = request;\n",
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F1')).toEqual([
      expectedMessage('F1', 'src/components/Card.tsx usa ../services/httpClient'),
    ]);
  });

  it('F1 detecta un componente que importa solo tipos de hooks', () => {
    const sources: Sources = {
      'src/components/Card.tsx':
        "import type { Thing } from '../hooks/useThing';\nexport function Card(props: Thing) { return props; }\n",
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F1')).toEqual([expectedMessage('F1', 'src/components/Card.tsx usa ../hooks/useThing')]);
  });

  it('F1 detecta un componente que usa el router, como TopBar antes de corregirlo', () => {
    const sources: Sources = {
      'src/components/Logo.tsx':
        "import { Link } from 'react-router';\nexport function Logo() { return <Link to=\"/pizarra\">La Pizarra</Link>; }\n",
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F1')).toEqual([
      '[F1] src/components/Logo.tsx usa react-router: los componentes reciben datos y callbacks por props; no conocen la API, el router, los hooks ni los contextos (Constitución, sección Frontend)',
    ]);
  });

  it('F1 detecta un componente que reexporta un hook', () => {
    const sources: Sources = {
      'src/components/Card.tsx': "export { useThing } from '../hooks/useThing';\n",
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F1')).toEqual([expectedMessage('F1', 'src/components/Card.tsx usa ../hooks/useThing')]);
  });

  it('F1 deja que un componente use su CSS Module, types, utils y react', () => {
    const sources: Sources = {
      'src/components/Card.tsx': [
        "import { useState } from 'react';",
        "import type { Player } from '../types/player';",
        "import { formatPrice } from '../utils/format';",
        "import styles from './Card.module.css';",
        'export function Card({ player }: { player: Player }) {',
        '  const [open] = useState(false);',
        '  return <p className={styles.card}>{open ? formatPrice(player.price) : player.name}</p>;',
        '}',
      ].join('\n'),
    };

    const violations = analyze(sources);

    expect(violations).toEqual([]);
  });

  it('F2 detecta un util que usa react', () => {
    const sources: Sources = {
      'src/utils/format.ts': "import { useMemo } from 'react';\nexport const memo = useMemo;\n",
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F2')).toEqual([expectedMessage('F2', 'src/utils/format.ts usa react')]);
  });

  it('F3 detecta un tipo que usa services', () => {
    const sources: Sources = {
      'src/types/player.ts': "import type { ApiClient } from '../services/api';\nexport type Player = { client: ApiClient };\n",
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F3')).toEqual([expectedMessage('F3', 'src/types/player.ts usa ../services/api')]);
  });

  it('F4 detecta un service que usa hooks', () => {
    const sources: Sources = {
      'src/services/api.ts': "import { useSession } from '../hooks/useSession';\nexport const session = useSession;\n",
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F4')).toEqual([expectedMessage('F4', 'src/services/api.ts usa ../hooks/useSession')]);
  });

  it('F5 detecta un hook que usa un componente', () => {
    const sources: Sources = {
      'src/hooks/useThing.ts': "import { Card } from '../components/Card';\nexport const useThing = () => Card;\n",
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F5')).toEqual([expectedMessage('F5', 'src/hooks/useThing.ts usa ../components/Card')]);
  });

  it('F6.1 detecta a main.tsx usando una página', () => {
    const sources: Sources = {
      'src/main.tsx': "import { BoardPage } from './pages/BoardPage';\nexport const start = BoardPage;\n",
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F6.1')).toEqual([expectedMessage('F6.1', 'src/main.tsx usa ./pages/BoardPage')]);
  });

  it('F6.2 detecta una página que carga el router con un import() dinámico', () => {
    const sources: Sources = {
      'src/pages/BoardPage.tsx': "export const loadRouter = () => import('../router/AppRouter');\n",
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F6.2')).toEqual([
      expectedMessage('F6.2', 'src/pages/BoardPage.tsx usa ../router/AppRouter'),
    ]);
  });

  it('F7 detecta un hook que llama a fetch', () => {
    const sources: Sources = {
      'src/hooks/useThing.ts': "export const useThing = () => fetch('/api/players');\n",
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F7')).toEqual([
      '[F7] src/hooks/useThing.ts usa fetch: los componentes y las páginas nunca llaman a fetch directo, y la sesión se guarda solo desde services (Constitución, sección Frontend)',
    ]);
  });

  it('F7 detecta sessionStorage por window y localStorage por globalThis', () => {
    const sources: Sources = {
      'src/pages/BoardPage.tsx': [
        "export const token = window.sessionStorage.getItem('token');",
        'export const storage = globalThis.localStorage;',
      ].join('\n'),
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F7')).toEqual([
      expectedMessage('F7', 'src/pages/BoardPage.tsx usa sessionStorage'),
      expectedMessage('F7', 'src/pages/BoardPage.tsx usa localStorage'),
    ]);
  });

  it('F7 no cuenta un comentario, un import de services ni la propiedad fetch de otro objeto', () => {
    const sources: Sources = {
      'src/context/SessionContext.tsx': [
        '// La sesión se guarda en sessionStorage, siempre a través de services',
        "import { readSession } from '../services/sessionStorage';",
        'const config = { fetch: readSession };',
        'export const currentSession = () => config.fetch();',
      ].join('\n'),
    };

    const violations = analyze(sources);

    expect(violations).toEqual([]);
  });

  it('F8.1 detecta un framework de UI', () => {
    const sources: Sources = {
      'src/pages/BoardPage.tsx': "import { Button } from '@mui/material';\nexport const Action = Button;\n",
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F8.1')).toEqual([
      expectedMessage('F8.1', 'src/pages/BoardPage.tsx usa @mui/material'),
    ]);
  });

  it('F8.1 rechaza react-router-dom y acepta react-dom/client', () => {
    const sources: Sources = {
      'src/pages/BoardPage.tsx': "import { Link } from 'react-router-dom';\nexport const Go = Link;\n",
      'src/main.tsx': "import { createRoot } from 'react-dom/client';\nexport const root = createRoot;\n",
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F8.1')).toEqual([
      expectedMessage('F8.1', 'src/pages/BoardPage.tsx usa react-router-dom'),
    ]);
  });

  it('F8.2 detecta un import de mockups', () => {
    const sources: Sources = {
      'src/pages/BoardPage.tsx': "import { board } from '../../mockups/board';\nexport const draft = board;\n",
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F8.2')).toEqual([
      expectedMessage('F8.2', 'src/pages/BoardPage.tsx usa ../../mockups/board'),
    ]);
  });

  it('F9 detecta un ciclo directo entre dos archivos', () => {
    const sources: Sources = {
      'src/utils/a.ts': "import { b } from './b';\nexport const a = () => b;\n",
      'src/utils/b.ts': "import { a } from './a';\nexport const b = () => a;\n",
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F9')).toEqual([
      '[F9] ciclo src/utils/a.ts -> src/utils/b.ts -> src/utils/a.ts: un ciclo ata dos archivos que tienen que poder entenderse y probarse por separado (Constitución, sección Frontend)',
    ]);
  });

  it('F9 detecta un ciclo indirecto una sola vez, aunque se entre por el medio', () => {
    const sources: Sources = {
      'src/pages/BoardPage.tsx': "import { b } from '../utils/b';\nexport const page = b;\n",
      'src/utils/a.ts': "import { b } from './b';\nexport const a = () => b;\n",
      'src/utils/b.ts': "import { c } from './c';\nexport const b = () => c;\n",
      'src/utils/c.ts': "import { a } from './a';\nexport const c = () => a;\n",
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F9')).toEqual([
      expectedMessage('F9', 'ciclo src/utils/a.ts -> src/utils/b.ts -> src/utils/c.ts -> src/utils/a.ts'),
    ]);
  });

  it('F10.1 detecta un test dentro de src', () => {
    const sources: Sources = {
      'src/components/Card.test.tsx': '',
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F10.1')).toEqual([
      '[F10.1] src/components/Card.test.tsx es un test dentro de src/: los tests viven solo en frontend/tests/, nunca junto al código (Constitución, sección Frontend)',
    ]);
  });

  it('F10.1 deja los tests de src fuera de las reglas de dependencias', () => {
    const sources: Sources = {
      'src/components/Card.test.tsx': "import { request } from '../services/api';\nexport const load = request;\n",
    };

    const violations = analyze(sources);

    expect(violations.map((violation) => violation.ruleId)).toEqual(['F10.1']);
  });

  it('F10.2 detecta un test de utils sin su archivo en src', () => {
    const sources: Sources = {
      'tests/utils/foo.test.ts': '',
    };

    const violations = analyze(sources);

    expect(messagesOf(violations, 'F10.2')).toEqual([
      '[F10.2] tests/utils/foo.test.ts no tiene src/utils/foo.ts ni .tsx: tests/ replica la estructura de src/: el test de src/<carpeta>/X.tsx está en tests/<carpeta>/X.test.tsx (Constitución, sección Frontend)',
    ]);
  });

  it('F10.2 acepta un test .tsx de un hook .ts y no revisa tests/pages', () => {
    const sources: Sources = {
      'src/hooks/useCatalog.ts': 'export const useCatalog = () => [];\n',
      'tests/hooks/useCatalog.test.tsx': '',
      'tests/pages/Flow.test.tsx': '',
    };

    const violations = analyze(sources);

    expect(violations).toEqual([]);
  });
});
