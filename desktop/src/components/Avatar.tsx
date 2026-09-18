type Props = {
  name: string;
  online?: boolean;
  size?: 'sm' | 'md';
};

export function Avatar({ name, online, size = 'md' }: Props) {
  const letter = (name.trim()[0] || '?').toUpperCase();
  return (
    <div className={`avatar ${size === 'sm' ? 'sm' : ''}`}>
      {letter}
      {online ? <span className="dot" /> : null}
    </div>
  );
}
