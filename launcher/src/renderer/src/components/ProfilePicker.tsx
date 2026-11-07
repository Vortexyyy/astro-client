import type { Profile } from '@shared/types';

interface Props {
  profiles: Profile[];
  value: string | null;
  onChange(id: string): void;
}

export default function ProfilePicker({ profiles, value, onChange }: Props) {
  if (profiles.length === 0) return <span className="muted">No profiles yet</span>;
  return (
    <select className="input" value={value ?? ''} onChange={(e) => onChange(e.target.value)} aria-label="Profile">
      {profiles.map((p) => (
        <option key={p.id} value={p.id}>
          {p.name} · {p.mcVersion}
        </option>
      ))}
    </select>
  );
}
