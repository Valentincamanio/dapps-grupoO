// Registro: formulario en tiza y revelación única de la clave de API.
import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { ApiKeyReveal } from '../components/ApiKeyReveal';
import { ChalkButton } from '../components/ChalkButton';
import { ChalkInput } from '../components/ChalkInput';
import { ChalkNote } from '../components/ChalkNote';
import { ChalkText } from '../components/ChalkText';
import { useLeaveGuard } from '../hooks/useLeaveGuard';
import { useSession } from '../hooks/useSession';
import { register } from '../services/authService';
import { ApiRequestError } from '../services/httpClient';
import { toFormErrors, type FormErrors } from '../utils/formErrors';
import styles from './RegisterPage.module.css';

const FIELDS = ['username', 'email', 'password'];
const NO_ERRORS: FormErrors = { fields: {} };

export function RegisterPage() {
  const { login } = useSession();
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<FormErrors>(NO_ERRORS);
  const [networkError, setNetworkError] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  // La clave vive solo acá, en el estado local de la página
  const [apiKey, setApiKey] = useState<string | null>(null);
  const [finished, setFinished] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);

  const guard = useLeaveGuard(apiKey !== null && !finished);

  // Se navega recién cuando la guarda ya está desactivada
  useEffect(() => {
    if (finished) navigate('/pizarra', { replace: true });
  }, [finished, navigate]);

  async function submit() {
    if (submitting) return;
    setSubmitting(true);
    setErrors(NO_ERRORS);
    setNetworkError(false);
    try {
      const response = await register({ username, email, password });
      setApiKey(response.apiKey);
    } catch (error) {
      if (error instanceof ApiRequestError) {
        const offline = error.apiError.status === 0;
        setNetworkError(offline);
        // Con error de red se conserva la contraseña para poder reintentar
        if (!offline) setPassword('');
        setErrors(toFormErrors(error.apiError, FIELDS));
      } else {
        setPassword('');
        setErrors({ fields: {}, general: 'no se pudo completar el registro' });
      }
    } finally {
      setSubmitting(false);
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void submit();
  }

  async function handleContinue() {
    setLoginError(null);
    try {
      await login(username, password);
      setApiKey(null);
      setUsername('');
      setEmail('');
      setPassword('');
      setFinished(true);
    } catch (error) {
      setLoginError(
        error instanceof ApiRequestError ? error.apiError.message : 'no se pudo iniciar sesión',
      );
    }
  }

  if (apiKey !== null) {
    return (
      <div className={styles.form}>
        <ApiKeyReveal apiKey={apiKey} onContinue={() => void handleContinue()} />
        {loginError ? <ChalkNote variant="error">{loginError}</ChalkNote> : null}
        {guard.isBlocked ? (
          <ChalkNote variant="info">
            <span>la clave no se vuelve a mostrar, ¿salir igual?</span>{' '}
            <ChalkButton onClick={guard.proceed}>salir</ChalkButton>{' '}
            <ChalkButton onClick={guard.stay}>quedarme</ChalkButton>
          </ChalkNote>
        ) : null}
      </div>
    );
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <ChalkText as="h1" variant="title">
        registrarse
      </ChalkText>
      <ChalkInput
        label="usuario"
        name="username"
        autoComplete="username"
        value={username}
        error={errors.fields.username}
        onChange={(event) => setUsername(event.target.value)}
      />
      <ChalkInput
        label="correo"
        name="email"
        type="email"
        autoComplete="email"
        value={email}
        error={errors.fields.email}
        onChange={(event) => setEmail(event.target.value)}
      />
      <ChalkInput
        label="contraseña"
        name="password"
        type="password"
        autoComplete="new-password"
        value={password}
        error={errors.fields.password}
        onChange={(event) => setPassword(event.target.value)}
      />
      {errors.general ? (
        <ChalkNote
          variant="error"
          action={networkError ? { label: 'reintentar', onClick: () => void submit() } : undefined}
        >
          {errors.general}
        </ChalkNote>
      ) : null}
      {submitting ? <ChalkNote variant="loading" /> : null}
      <ChalkButton type="submit" busy={submitting}>
        registrarse
      </ChalkButton>
      <ChalkText variant="hint">
        ¿ya tenés cuenta? <Link to="/login">entrar</Link>
      </ChalkText>
    </form>
  );
}
