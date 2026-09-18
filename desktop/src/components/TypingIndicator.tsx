type Props = {
  visible: boolean;
};

export function TypingIndicator({ visible }: Props) {
  return (
    <div className={`typing-indicator-wrap ${visible ? 'visible' : ''}`} aria-hidden={!visible}>
      <div className="typing-indicator-inner">
        <div className="typing-bubble">
          <span className="typing-dot" />
          <span className="typing-dot" />
          <span className="typing-dot" />
        </div>
      </div>
    </div>
  );
}
