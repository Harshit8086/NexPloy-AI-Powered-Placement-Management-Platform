"use client";

import { forwardRef } from "react";
import type { ResumeData } from "./resumeBuilderTypes";

// Renders bullet points from a newline-separated string, stripping any leading "-"/"•".
function Bullets({ text }: { text: string }) {
  const lines = text
    .split("\n")
    .map((l) => l.trim())
    .filter(Boolean);
  if (lines.length === 0) return null;
  return (
    <ul className="ml-4 mt-1 list-disc p-0">
      {lines.map((line, i) => (
        <li key={i} className="mb-[1px] text-[11px] leading-[1.6] text-black">
          {line.replace(/^[-•]\s*/, "")}
        </li>
      ))}
    </ul>
  );
}

const SectionHeading = ({ children }: { children: React.ReactNode }) => (
  <h2 className="mb-[9px] mt-0 border-b-[1.5px] border-black pb-[3px] text-[10.5px] font-bold uppercase tracking-[0.14em] text-black">
    {children}
  </h2>
);

/**
 * Turns whatever the student typed (a full URL, a `linkedin.com/in/…` path, or a bare handle) into a
 * valid https href, without ever double-prefixing. If a domain is already present it is kept as-is;
 * a bare handle gets the canonical base for its kind. Returns null for empty input.
 */
function toHref(value: string, kind: "linkedin" | "github" | "url"): string | null {
  let v = value.trim();
  if (!v) return null;
  v = v.replace(/^https?:\/\//i, "").replace(/^www\./i, "").replace(/^\/+/, "");
  if (!v.includes(".")) {
    if (kind === "linkedin") v = `linkedin.com/in/${v}`;
    else if (kind === "github") v = `github.com/${v}`;
  }
  return `https://${v}`;
}

// A clickable link rendered as a clean label (e.g. "LinkedIn") rather than a raw URL. The full
// address is preserved in the href so the exported PDF stays clickable.
const ResumeLink = ({ href, children }: { href: string; children: React.ReactNode }) => (
  <a href={href} target="_blank" rel="noreferrer" className="text-black underline">
    {children}
  </a>
);

/**
 * A single-column, ATS-friendly résumé rendered on an A4 page in black serif text.
 * Exposed via forwardRef so the builder page can hand its DOM node to react-to-print.
 * Purely presentational — it renders exactly what the form holds, never invented content.
 */
export const ATSTemplate = forwardRef<HTMLDivElement, { data: ResumeData }>(function ATSTemplate(
  { data },
  ref,
) {
  const skillsList = data.skills
    ? data.skills.split(",").map((s) => s.trim()).filter(Boolean)
    : [];
  const half = Math.ceil(skillsList.length / 2);
  const skillsCol1 = skillsList.slice(0, half);
  const skillsCol2 = skillsList.slice(half);

  const linkedinHref = toHref(data.linkedin, "linkedin");
  const githubHref = toHref(data.github, "github");

  // Contact line: plain text for email/phone/location, clean labelled links for the profiles.
  const contacts: React.ReactNode[] = [];
  if (data.email) contacts.push(data.email);
  if (data.phone) contacts.push(data.phone);
  if (data.location) contacts.push(data.location);
  if (linkedinHref) contacts.push(<ResumeLink href={linkedinHref}>LinkedIn</ResumeLink>);
  if (githubHref) contacts.push(<ResumeLink href={githubHref}>GitHub</ResumeLink>);

  return (
    <div
      ref={ref}
      className="box-border min-h-[297mm] w-[210mm] bg-white px-[18mm] py-[15mm] text-black"
      style={{ fontFamily: "'Times New Roman', Times, serif" }}
    >
      <div className="mb-[10px] border-b-2 border-black pb-[10px] text-center">
        <h2 className="m-0 mb-[7px] text-[28px] font-bold uppercase tracking-[0.08em]">
          {data.name || "YOUR NAME"}
        </h2>
        <div className="flex flex-wrap justify-center text-[10.5px] text-black">
          {contacts.map((v, i, arr) => (
            <span key={i} className="whitespace-nowrap">
              {v}
              {i < arr.length - 1 && <span className="mx-[7px] text-black">|</span>}
            </span>
          ))}
        </div>
      </div>

      {data.summary && (
        <section className="mb-[13px]">
          <SectionHeading>Professional Summary</SectionHeading>
          <p className="m-0 text-[11px] leading-[1.65] text-black">{data.summary}</p>
        </section>
      )}

      {skillsList.length > 0 && (
        <section className="mb-[13px]">
          <SectionHeading>Technical Skills</SectionHeading>
          <div className="grid grid-cols-2 gap-x-[20px] gap-y-[2px]">
            <ul className="m-0 list-disc pl-4">
              {skillsCol1.map((skill, i) => (
                <li key={i} className="text-[11px] capitalize leading-[1.7] text-black">
                  {skill}
                </li>
              ))}
            </ul>
            <ul className="m-0 list-disc pl-4">
              {skillsCol2.map((skill, i) => (
                <li key={i} className="text-[11px] capitalize leading-[1.7] text-black">
                  {skill}
                </li>
              ))}
            </ul>
          </div>
        </section>
      )}

      {data.experience.length > 0 && (
        <section className="mb-[13px]">
          <SectionHeading>Work Experience</SectionHeading>
          {data.experience.map((exp, i) => (
            <div key={i} className="mb-[11px] break-inside-avoid print:break-inside-avoid">
              <div className="flex items-baseline justify-between">
                <span className="text-[12px] font-bold text-black">{exp.role}</span>
                <span className="ml-2 whitespace-nowrap text-[10.5px] text-black">{exp.duration}</span>
              </div>
              <div className="mb-[2px] text-[11px] italic text-black">{exp.company}</div>
              <Bullets text={exp.description} />
            </div>
          ))}
        </section>
      )}

      {data.projects.length > 0 && (
        <section className="mb-[13px]">
          <SectionHeading>Projects</SectionHeading>
          {data.projects.map((proj, i) => (
            <div key={i} className="mb-[11px] break-inside-avoid print:break-inside-avoid">
              <div className="flex items-baseline justify-between">
                <span className="text-[12px] font-bold text-black">{proj.name}</span>
                {(() => {
                  const gh = toHref(proj.github, "github");
                  const live = toHref(proj.liveLink, "url");
                  if (!gh && !live) return null;
                  return (
                    <span className="ml-2 whitespace-nowrap text-[10.5px] text-black">
                      {gh && <ResumeLink href={gh}>GitHub</ResumeLink>}
                      {gh && live && <span className="mx-[6px]">|</span>}
                      {live && <ResumeLink href={live}>Live</ResumeLink>}
                    </span>
                  );
                })()}
              </div>
              {proj.techStack && (
                <div className="mb-[2px] text-[11px] italic text-black">
                  <span className="font-bold">Tech Stack: </span>
                  {proj.techStack}
                </div>
              )}
              <Bullets text={proj.description} />
            </div>
          ))}
        </section>
      )}

      {data.education.length > 0 && (
        <section className="mb-[13px]">
          <SectionHeading>Education</SectionHeading>
          {data.education.map((edu, i) => (
            <div key={i} className="mb-[11px] break-inside-avoid print:break-inside-avoid">
              <div className="flex items-baseline justify-between">
                <span className="text-[12px] font-bold text-black">
                  {edu.degree}
                  {edu.branch ? ` in ${edu.branch}` : ""}
                </span>
                <span className="ml-2 whitespace-nowrap text-[10.5px] text-black">{edu.year}</span>
              </div>
              <div className="mb-[2px] text-[11px] italic text-black">
                {edu.college}
                {edu.cgpa && (
                  <span className="ml-[6px]">
                    | CGPA: <strong>{edu.cgpa}</strong>
                  </span>
                )}
              </div>
            </div>
          ))}
        </section>
      )}
    </div>
  );
});
