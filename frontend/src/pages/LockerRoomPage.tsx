// El vestuario: perfil, cambio de contraseña, regeneración de la clave de API y cierre de sesión.
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { ApiKeyReveal } from '../components/ApiKeyReveal';
import { ChalkButton } from '../components/ChalkButton';
import { ChalkInput } from '../components/ChalkInput';
import { ChalkNote } from '../components/ChalkNote';
import { ChalkText } from '../components/ChalkText';
import { useLeaveGuard } from '../hooks/useLeaveGuard';
import { useSession } from '../hooks/useSession';
import { changePassword, regenerateApiKey } from '../services/authService';
import { ApiRequestError } from '../services/httpClient';
import { formatBalance } from '../utils/format';
import { toFormErrors, type FormErrors } from '../utils/formErrors';
import { roleLabel } from '../utils/positions';
import styles from './LockerRoomPage.module.css';

const FIELDS = ['currentPassword', 'newPassword'];
const NO_ERRORS: FormErrors = { fields: {} };

export function LockerRoomPage() {
  const { profile, logout } = useSession();
  const navigate = useNavigate();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [errors, setErrors] = useState<FormErrors>(NO_ERRORS);
  const [networkError, setNetworkError] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [passwordChanged, setPasswordChanged] = useState(false);

  const [confirmingKey, setConfirmingKey] = useState(false);
  const [regenerating, setRegenerating] = useState(false);
  const [keyError, setKeyError] = useState<string | null>(null);
  const [keyNetworkError, setKeyNetworkError] = useState(false);
  // La clave nueva vive solo acá, en el estado local de la página
  const [apiKey, setApiKey] = useState<string | null>(null);

  const guard = useLeaveGuard(apiKey !== null);

  async function submitPassword() {
    if (submitting) return;
    setSubmitting(true);
    setErrors(NO_ERRORS);
    setNetworkError(false);
    setPasswordChanged(false);
    try {
      await changePassword({ currentPassword, newPassword });
      setCurrentPassword('');
      setNewPassword('');
      setPasswordChanged(true);
    } catch (error) {
      if (error instanceof ApiRequestError) {
        setNetworkError(error.apiError.status === 0);
        const result = toFormErrors(error.apiError, FIELDS);
        // La API no marca el campo cuando la contraseña actual no coincide
        if (
          error.apiError.status === 400 &&
          !error.apiError.violations?.length &&
          error.apiError.message.toLowerCase().includes('actual')
        ) {
          result.fields.currentPassword = error.apiError.message;
          delete result.general;
        }
        setErrors(result);
      } else {
        setErrors({ fields: {}, general: 'no se pudo cambiar la contraseña' });
      }
    } finally {
      setSubmitting(false);
    }
  }

  function handlePasswordSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void submitPassword();
  }

  async function confirmRegenerate() {
    if (regenerating) return;
    setRegenerating(true);
    setKeyError(null);
    setKeyNetworkError(false);
    try {
      const response = await regenerateApiKey();
      setApiKey(response.apiKey);
      setConfirmingKey(false);
    } catch (error) {
      if (error instanceof ApiRequestError) {
        setKeyNetworkError(error.apiError.status === 0);
        setKeyError(error.apiError.message);
      } else {
        setKeyError('no se pudo regenerar la clave');
      }
    } finally {
      setRegenerating(false);
    }
  }

  async function handleLogout() {
    // Se navega antes de cerrar la sesión para que la guarda de rutas no mande a /login
    await navigate('/pizarra', { replace: true });
    logout();
  }

  if (apiKey !== null) {
    return (
      <section className={styles.locker}>
        <ApiKeyReveal apiKey={apiKey} continueLabel="listo" onContinue={() => setApiKey(null)} />
        {guard.isBlocked ? (
          <ChalkNote variant="info">
            <span>la clave no se vuelve a mostrar, ¿salir igual?</span>{' '}
            <ChalkButton onClick={guard.proceed}>salir</ChalkButton>{' '}
            <ChalkButton onClick={guard.stay}>quedarme</ChalkButton>
          </ChalkNote>
        ) : null}
      </section>
    );
  }

  return (
    <section className={styles.locker}>
      <ChalkText as="h1" variant="title">
        el vestuario
      </ChalkText>

      {profile ? (
        <dl className={styles.profile}>
          <div>
            <dt>usuario</dt>
            <dd>{profile.username}</dd>
          </div>
          <div>
            <dt>correo</dt>
            <dd>{profile.email}</dd>
          </div>
          <div>
            <dt>rol</dt>
            <dd>{roleLabel(profile.role)}</dd>
          </div>
          <div>
            <dt>saldo</dt>
            <dd>{formatBalance(profile.balance)}</dd>
          </div>
        </dl>
      ) : null}

      <form className={styles.form} onSubmit={handlePasswordSubmit} noValidate>
        <ChalkText as="h2" variant="heading">
          cambiar contraseña
        </ChalkText>
        <ChalkInput
          label="contraseña actual"
          name="currentPassword"
          type="password"
          autoComplete="current-password"
          value={currentPassword}
          error={errors.fields.currentPassword}
          onChange={(event) => setCurrentPassword(event.target.value)}
        />
        <ChalkInput
          label="contraseña nueva"
          name="newPassword"
          type="password"
          autoComplete="new-password"
          value={newPassword}
          error={errors.fields.newPassword}
          onChange={(event) => setNewPassword(event.target.value)}
        />
        {errors.general ? (
          <ChalkNote
            variant="error"
            action={
              networkError ? { label: 'reintentar', onClick: () => void submitPassword() } : undefined
            }
          >
            {errors.general}
          </ChalkNote>
        ) : null}
        {passwordChanged ? <ChalkNote variant="success">contraseña cambiada</ChalkNote> : null}
        {submitting ? <ChalkNote variant="loading" /> : null}
        <ChalkButton type="submit" busy={submitting}>
          cambiar contraseña
        </ChalkButton>
      </form>

      <div className={styles.form}>
        <ChalkText as="h2" variant="heading">
          clave de API
        </ChalkText>
        {confirmingKey ? (
          <ChalkNote variant="info">
            <span>la clave actual deja de funcionar en cuanto generes la nueva, ¿seguimos?</span>{' '}
            <ChalkButton busy={regenerating} onClick={() => void confirmRegenerate()}>
              sí, regenerar
            </ChalkButton>{' '}
            <ChalkButton
              disabled={regenerating}
              onClick={() => {
                setConfirmingKey(false);
                setKeyError(null);
              }}
            >
              cancelar
            </ChalkButton>
          </ChalkNote>
        ) : (
          <ChalkButton onClick={() => setConfirmingKey(true)}>regenerar clave</ChalkButton>
        )}
        {keyError ? (
          <ChalkNote
            variant="error"
            action={
              keyNetworkError
                ? { label: 'reintentar', onClick: () => void confirmRegenerate() }
                : undefined
            }
          >
            {keyError}
          </ChalkNote>
        ) : null}
      </div>

      <ChalkButton onClick={() => void handleLogout()}>cerrar sesión</ChalkButton>
    </section>
  );
}
