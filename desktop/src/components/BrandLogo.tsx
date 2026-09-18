type Props = {
  size?: number;
  showText?: boolean;
  className?: string;
};

export function BrandLogo({ size = 36, showText = true, className = '' }: Props) {
  return (
    <div className={`brand-logo-wrap ${className}`} style={{ gap: showText ? 10 : 0 }}>
      <img
        src="./logo.png"
        alt="Deep Messenger"
        width={size}
        height={size}
        className="brand-logo-img"
        draggable={false}
      />
      {showText ? <span className="brand-logo-text">Deep</span> : null}
    </div>
  );
}
