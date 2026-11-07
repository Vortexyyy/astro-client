import type { CSSProperties } from 'react';

/**
 * Minecraft head cropped from the account's skin texture with CSS (face + hat layer),
 * so no third-party avatar service is needed.
 */
export default function SkinHead({ skinUrl, name, size = 28 }: { skinUrl?: string; name: string; size?: number }) {
  if (!skinUrl) {
    return (
      <span className="skin-head fallback" style={{ width: size, height: size }} aria-hidden="true">
        {name.slice(0, 1).toUpperCase()}
      </span>
    );
  }
  const style = { width: size, height: size, '--skin': `url("${skinUrl}")`, '--px': `${size / 8}px` } as CSSProperties;
  return <span className="skin-head" style={style} aria-hidden="true" />;
}
