import Link from "next/link";

interface SectionHeadingProps {
  title: string;
  id?: string;
  link?: { label: string; href: string };
}

export function SectionHeading({ title, id, link }: SectionHeadingProps) {
  return (
    <div className="section-heading reveal" id={id}>
      <h2>{title}</h2>
      {link && <Link href={link.href}>{link.label}</Link>}
    </div>
  );
}
