// Texto escrito en tiza sobre el pizarrón.
import type { ElementType, ReactNode } from 'react';
import styles from './ChalkText.module.css';

export type ChalkTextVariant = 'title' | 'heading' | 'body' | 'hint' | 'scribble';

interface ChalkTextProps {
  as?: ElementType;
  variant?: ChalkTextVariant;
  className?: string;
  children: ReactNode;
}

export function ChalkText({
  as: Tag = 'p',
  variant = 'body',
  className,
  children,
}: ChalkTextProps) {
  const classes = [styles.text, styles[variant], className].filter(Boolean).join(' ');
  return <Tag className={classes}>{children}</Tag>;
}
