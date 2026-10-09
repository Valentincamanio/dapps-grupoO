// El vestuario: cuenta de la persona (versión inicial; la historia 6 la completa).
import { ChalkText } from '../components/ChalkText';
import { useSession } from '../hooks/useSession';
import styles from './LockerRoomPage.module.css';

export function LockerRoomPage() {
  const { profile } = useSession();

  return (
    <section className={styles.locker}>
      <ChalkText as="h1" variant="title">
        el vestuario
      </ChalkText>
      {profile ? <ChalkText variant="body">{profile.username}</ChalkText> : null}
    </section>
  );
}
