// Inicio de sesión: formulario en tiza que vuelve a la pantalla de origen al entrar.
import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router';
import { ChalkButton } from '../components/ChalkButton';
import { ChalkInput } from '../components/ChalkInput';
import { ChalkNote } from '../components/ChalkNote';
import { ChalkText } from '../components/ChalkText';
import { useSession } from '../hooks/useSession';
import { ApiRequestError } from '../services/httpClient';
import styles from './LoginPage.module.css';

interface LoginLocationState {
  from?: { pathname: string; search?: string };
  reason?: 'required' | 'expired';
}

const REASON_TEXT = {
  required: 'para entrar al vestuario tenés que iniciar sesión',
  expired: 'tu sesión venció, volvé a entrar',
};

export function LoginPage() {
  const { status, login } = useSession();
  const navigate = useNavigate();
  const location = useLocation();
  const state = (location.state ?? {}) as LoginLocationState;

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [generalError, setGeneralError] = useState<string | null>(null);
  // Evita que la redirección por "ya hay sesión" pise el regreso al origen
  const [justLoggedIn, setJustLoggedIn] = useState(false);

  if (status === 'authenticated' && !justLoggedIn) {
    return <Navigate to="/pizarra" replace />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setGeneralError(null);
    setJustLoggedIn(true);
    try {
      await login(username, password);
      const target = state.from ? `${state.from.pathname}${state.from.search ?? ''}` : '/pizarra';
      navigate(target, { replace: true });
    } catch (error) {
      setJustLoggedIn(false);
      setGeneralError(
        error instanceof ApiRequestError ? error.apiError.message : 'no se pudo iniciar sesión',
      );
      setSubmitting(false);
    }
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <ChalkText as="h1" variant="title">
        entrar
      </ChalkText>
      {state.reason ? <ChalkNote variant="info">{REASON_TEXT[state.reason]}</ChalkNote> : null}
      <ChalkInput
        label="usuario"
        name="username"
        autoComplete="username"
        value={username}
        onChange={(event) => setUsername(event.target.value)}
      />
      <ChalkInput
        label="contraseña"
        name="password"
        type="password"
        autoComplete="current-password"
        value={password}
        onChange={(event) => setPassword(event.target.value)}
      />
      {generalError ? <ChalkNote variant="error">{generalError}</ChalkNote> : null}
      {submitting ? <ChalkNote variant="loading" /> : null}
      <ChalkButton type="submit" busy={submitting}>
        entrar
      </ChalkButton>
      <ChalkText variant="hint">
        ¿no tenés cuenta? <Link to="/register">registrarse</Link>
      </ChalkText>
    </form>
  );
}
