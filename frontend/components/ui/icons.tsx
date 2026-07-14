// Inline SVG icons carried over verbatim from the design. `size` sets width/height
// when provided (top-bar icons); omit it to let CSS size the icon (tab bar).
import type { SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement> & { size?: number };

function base({ size, ...props }: IconProps) {
  return {
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    ...(size ? { width: size, height: size } : {}),
    ...props,
  };
}

export function MenuIcon({ size = 22, ...p }: IconProps) {
  return (
    <svg {...base({ size, ...p })} strokeWidth="1.8" strokeLinecap="round">
      <path d="M4 6h16M4 12h16M4 18h16" />
    </svg>
  );
}

export function SearchIcon({ size, ...p }: IconProps) {
  return (
    <svg {...base({ size, ...p })} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="11" cy="11" r="7" />
      <path d="m21 21-4.3-4.3" />
    </svg>
  );
}

export function CartIcon({ size = 22, ...p }: IconProps) {
  return (
    <svg {...base({ size, ...p })} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="9" cy="20" r="1.4" />
      <circle cx="17.5" cy="20" r="1.4" />
      <path d="M2.5 3.5h2.4l2.5 12.2a1.6 1.6 0 0 0 1.6 1.3h8.6a1.6 1.6 0 0 0 1.6-1.3l1.7-8.7H6" />
    </svg>
  );
}

export function ChevronLeftIcon({ size = 22, ...p }: IconProps) {
  return (
    <svg {...base({ size, ...p })} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="m15 18-6-6 6-6" />
    </svg>
  );
}

export function ChevronRightIcon({ size = 22, ...p }: IconProps) {
  return (
    <svg {...base({ size, ...p })} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="m9 6 6 6-6 6" />
    </svg>
  );
}

export function CloseIcon({ size = 22, ...p }: IconProps) {
  return (
    <svg {...base({ size, ...p })} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M18 6 6 18M6 6l12 12" />
    </svg>
  );
}

export function MinusIcon({ size = 20, ...p }: IconProps) {
  return (
    <svg {...base({ size, ...p })} strokeWidth="1.8" strokeLinecap="round">
      <path d="M5 12h14" />
    </svg>
  );
}

export function PlusIcon({ size = 20, ...p }: IconProps) {
  return (
    <svg {...base({ size, ...p })} strokeWidth="1.8" strokeLinecap="round">
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}

export function FilterIcon({ size = 18, ...p }: IconProps) {
  return (
    <svg {...base({ size, ...p })} strokeWidth="1.8" strokeLinecap="round">
      <path d="M4 6h16M7 12h10M10 18h4" />
    </svg>
  );
}

export function SortIcon({ size = 18, ...p }: IconProps) {
  return (
    <svg {...base({ size, ...p })} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="m7 4 0 16M7 4 4 7m3-3 3 3M17 20l0-16m0 16 3-3m-3 3-3-3" />
    </svg>
  );
}

export function HomeIcon({ size, ...p }: IconProps) {
  return (
    <svg {...base({ size, ...p })} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1Z" />
    </svg>
  );
}

export function BagIcon({ size, ...p }: IconProps) {
  return (
    <svg {...base({ size, ...p })} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20.6 9.2 19 19.4a2 2 0 0 1-2 1.6H7a2 2 0 0 1-2-1.6L3.4 9.2" />
      <path d="M2.5 9.2h19" />
      <path d="M8.5 9V7a3.5 3.5 0 0 1 7 0v2" />
    </svg>
  );
}

export function HeartIcon({ size, ...p }: IconProps) {
  return (
    <svg {...base({ size, ...p })} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M19.8 5.6a5 5 0 0 0-7.1 0L12 6.3l-.7-.7a5 5 0 1 0-7.1 7.1l7.8 7.8 7.8-7.8a5 5 0 0 0 0-7.1Z" />
    </svg>
  );
}

export function UserIcon({ size, ...p }: IconProps) {
  return (
    <svg {...base({ size, ...p })} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21a8 8 0 0 1 16 0" />
    </svg>
  );
}
