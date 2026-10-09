"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import { toast } from "sonner";
import {
  User,
  Building2,
  Bell,
  ShieldCheck,
  SlidersHorizontal,
  Lock,
  Check,
  X,
  Loader2,
  LogOut,
  KeyRound,
  Clock,
  History,
  Smartphone,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { CardTitle } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { PageHeader } from "@/components/layout/PageHeader";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { useApiResource } from "@/hooks/useApiResource";
import {
  getActiveRecruiter,
  getRecruiterSettings,
  updateActiveRecruiter,
  updateRecruiterSettings,
  type RecruiterUpdateInput,
  type RecruiterSettingsInput,
} from "@/lib/api/recruiters";
import { changePassword, logout, logoutAllDevices } from "@/lib/api/auth";
import type { Recruiter, RecruiterSettings } from "@/types";

// ---- small labelled controls (match student/profile edit conventions) ----

function TextRow({
  label,
  value,
  onChange,
  placeholder,
  hint,
  readOnly,
  type = "text",
}: {
  label: string;
  value: string;
  onChange?: (v: string) => void;
  placeholder?: string;
  hint?: string;
  readOnly?: boolean;
  type?: string;
}) {
  return (
    <div>
      <Label className="text-xs text-muted-foreground">{label}</Label>
      <Input
        type={type}
        value={value}
        placeholder={placeholder}
        readOnly={readOnly}
        onChange={(e) => onChange?.(e.target.value)}
        className={`mt-1 h-9 text-sm ${readOnly ? "cursor-not-allowed opacity-70" : ""}`}
      />
      {hint && <p className="mt-0.5 text-[11px] text-muted-foreground/70">{hint}</p>}
    </div>
  );
}

function TextAreaRow({
  label,
  value,
  onChange,
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
}) {
  return (
    <div>
      <Label className="text-xs text-muted-foreground">{label}</Label>
      <textarea
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        rows={4}
        className="mt-1 w-full rounded-md border border-input bg-secondary/40 px-3 py-2 text-sm shadow-sm ring-offset-background placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2"
      />
    </div>
  );
}

function SwitchRow({
  label,
  description,
  checked,
  onChange,
}: {
  label: string;
  description?: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-border/60 py-3 last:border-0">
      <div className="min-w-0">
        <p className="text-sm font-medium">{label}</p>
        {description && <p className="text-xs text-muted-foreground">{description}</p>}
      </div>
      <Switch checked={checked} onCheckedChange={onChange} />
    </div>
  );
}

function SelectRow({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  options: { value: string; label: string }[];
}) {
  return (
    <div>
      <Label className="text-xs text-muted-foreground">{label}</Label>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger className="mt-1">
          <SelectValue placeholder="Select…" />
        </SelectTrigger>
        <SelectContent>
          {options.map((o) => (
            <SelectItem key={o.value} value={o.value}>
              {o.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}

/** Save / Cancel footer shared by every editable section. */
function SectionActions({
  saving,
  onSave,
  onCancel,
}: {
  saving: boolean;
  onSave: () => void;
  onCancel: () => void;
}) {
  return (
    <div className="mt-5 flex items-center gap-2">
      <Button size="sm" variant="glow" onClick={onSave} disabled={saving}>
        {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Check className="h-3.5 w-3.5" />}
        {saving ? "Saving…" : "Save changes"}
      </Button>
      <Button size="sm" variant="outline" onClick={onCancel} disabled={saving}>
        <X className="h-3.5 w-3.5" /> Cancel
      </Button>
    </div>
  );
}

type ProfileDraft = {
  name: string;
  phone: string;
  designation: string;
  logoInitial: string;
  companyName: string;
  companyIndustry: string;
  companyWebsite: string;
  companyLocation: string;
  companyDescription: string;
  companySize: string;
  companyLinkedin: string;
  companyTwitter: string;
  companyGithub: string;
};

function profileFrom(r: Recruiter): ProfileDraft {
  return {
    name: r.name ?? "",
    phone: r.phone ?? "",
    designation: r.designation ?? "",
    logoInitial: r.logoInitial ?? "",
    companyName: r.companyName ?? "",
    companyIndustry: r.industry ?? "",
    companyWebsite: r.website ?? "",
    companyLocation: r.hqLocation ?? "",
    companyDescription: r.companyDescription ?? "",
    companySize: r.companySize ?? "",
    companyLinkedin: r.companyLinkedin ?? "",
    companyTwitter: r.companyTwitter ?? "",
    companyGithub: r.companyGithub ?? "",
  };
}

const errMessage = (e: unknown, fallback: string) =>
  e instanceof Error && e.message ? e.message : fallback;

export default function RecruiterSettingsPage() {
  const router = useRouter();
  const { setTheme } = useTheme();
  // Bumped after a successful save so both resources refetch the persisted values.
  const [version, setVersion] = useState(0);

  const { data: recruiter, loading: rLoading, error: rError, reload } = useApiResource<Recruiter | null>(
    () => getActiveRecruiter(),
    [version],
    null,
  );
  const { data: settings } = useApiResource<RecruiterSettings | null>(
    () => getRecruiterSettings(),
    [version],
    null,
  );

  const [profile, setProfile] = useState<ProfileDraft | null>(null);
  const [prefs, setPrefs] = useState<RecruiterSettings | null>(null);
  useEffect(() => {
    if (recruiter) setProfile(profileFrom(recruiter));
  }, [recruiter]);
  useEffect(() => {
    if (settings) setPrefs(settings);
  }, [settings]);

  const [savingSection, setSavingSection] = useState<string | null>(null);

  // Password form (Security tab).
  const [pw, setPw] = useState({ current: "", next: "", confirm: "" });
  const [pwSaving, setPwSaving] = useState(false);
  const [signingOutAll, setSigningOutAll] = useState(false);

  const setP = (patch: Partial<ProfileDraft>) => setProfile((d) => (d ? { ...d, ...patch } : d));
  const setS = (patch: Partial<RecruiterSettings>) => setPrefs((d) => (d ? { ...d, ...patch } : d));

  const saveProfile = async (fields: RecruiterUpdateInput, section: string) => {
    setSavingSection(section);
    try {
      await updateActiveRecruiter(fields);
      setVersion((v) => v + 1);
      toast.success("Changes saved");
    } catch (e) {
      toast.error(errMessage(e, "Could not save your changes."));
    } finally {
      setSavingSection(null);
    }
  };

  const saveSettings = async (fields: RecruiterSettingsInput, section: string) => {
    setSavingSection(section);
    try {
      const updated = await updateRecruiterSettings(fields);
      setPrefs(updated);
      if (typeof fields.theme === "string" && (fields.theme === "dark" || fields.theme === "light")) {
        setTheme(fields.theme);
      }
      toast.success("Settings saved");
    } catch (e) {
      toast.error(errMessage(e, "Could not save your settings."));
    } finally {
      setSavingSection(null);
    }
  };

  const handleChangePassword = async () => {
    if (pw.next.length < 8) {
      toast.error("New password must be at least 8 characters.");
      return;
    }
    if (pw.next !== pw.confirm) {
      toast.error("New password and confirmation do not match.");
      return;
    }
    setPwSaving(true);
    try {
      await changePassword({ currentPassword: pw.current, newPassword: pw.next });
      setPw({ current: "", next: "", confirm: "" });
      toast.success("Password changed. Other devices have been signed out.");
    } catch (e) {
      toast.error(errMessage(e, "Could not change your password."));
    } finally {
      setPwSaving(false);
    }
  };

  const handleSignOutEverywhere = async () => {
    setSigningOutAll(true);
    try {
      await logoutAllDevices();
      logout();
      toast.success("Signed out of all devices.");
      router.push("/login");
    } catch (e) {
      toast.error(errMessage(e, "Could not sign out of all devices."));
      setSigningOutAll(false);
    }
  };

  if (!recruiter || !profile || !prefs) {
    return (
      <div className="mx-auto max-w-4xl">
        <PageHeader eyebrow="Account" title="Recruiter Settings" subtitle="Manage your account, company profile, and preferences." />
        {rLoading && <p className="py-10 text-center text-sm text-muted-foreground">Loading your settings…</p>}
        {!rLoading && rError && (
          <div className="flex flex-col items-center gap-3 py-10 text-center">
            <p className="text-sm text-risk" role="alert">{rError}</p>
            <Button size="sm" variant="outline" onClick={reload}>Retry</Button>
          </div>
        )}
        {!rLoading && !rError && (
          <p className="py-10 text-center text-sm text-muted-foreground">Sign in as a recruiter to manage your settings.</p>
        )}
      </div>
    );
  }

  const busy = (s: string) => savingSection === s;

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader
        eyebrow="Account"
        title="Recruiter Settings"
        subtitle="Manage your account, company profile, notifications, security, and preferences."
      />
      <Tabs defaultValue="account" className="w-full">
        <TabsList className="flex h-auto flex-wrap justify-start gap-1">
          <TabsTrigger value="account" className="gap-1.5"><User className="h-3.5 w-3.5" /> Account</TabsTrigger>
          <TabsTrigger value="company" className="gap-1.5"><Building2 className="h-3.5 w-3.5" /> Company</TabsTrigger>
          <TabsTrigger value="notifications" className="gap-1.5"><Bell className="h-3.5 w-3.5" /> Notifications</TabsTrigger>
          <TabsTrigger value="security" className="gap-1.5"><ShieldCheck className="h-3.5 w-3.5" /> Security</TabsTrigger>
          <TabsTrigger value="preferences" className="gap-1.5"><SlidersHorizontal className="h-3.5 w-3.5" /> Preferences</TabsTrigger>
          <TabsTrigger value="privacy" className="gap-1.5"><Lock className="h-3.5 w-3.5" /> Privacy</TabsTrigger>
        </TabsList>

        {/* ACCOUNT */}
        <TabsContent value="account">
          <DepthCard className="p-5">
            <div className="mb-4 flex items-center gap-4">
              <Avatar className="h-16 w-16">
                <AvatarFallback className="text-lg">{profile.logoInitial || recruiter.logoInitial}</AvatarFallback>
              </Avatar>
              <div>
                <CardTitle>{recruiter.companyName}</CardTitle>
                <p className="text-xs text-muted-foreground">{recruiter.email}</p>
              </div>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <TextRow label="Full Name" value={profile.name} onChange={(v) => setP({ name: v })} placeholder="Your name" />
              <TextRow label="Work Email" value={recruiter.email} readOnly hint="Your login email — contact support to change it." />
              <TextRow label="Phone Number" value={profile.phone} onChange={(v) => setP({ phone: v })} placeholder="e.g. +91 98765 43210" />
              <TextRow label="Designation" value={profile.designation} onChange={(v) => setP({ designation: v })} placeholder="e.g. Talent Acquisition Lead" />
              <TextRow label="Avatar Initial" value={profile.logoInitial} onChange={(v) => setP({ logoInitial: v.slice(0, 4) })} hint="Up to 4 characters shown in your avatar." />
            </div>
            <SectionActions
              saving={busy("account")}
              onSave={() =>
                saveProfile(
                  { name: profile.name, phone: profile.phone, designation: profile.designation, logoInitial: profile.logoInitial },
                  "account",
                )
              }
              onCancel={() => setProfile(profileFrom(recruiter))}
            />
          </DepthCard>
        </TabsContent>

        {/* COMPANY */}
        <TabsContent value="company">
          <DepthCard className="p-5">
            <div className="mb-1 flex items-center gap-2">
              <Building2 className="h-4 w-4 text-violet-bright" />
              <CardTitle>Company Information</CardTitle>
            </div>
            <p className="mb-4 text-xs text-muted-foreground">
              This is your editable company record. The public Company Profile students and officers see reads from these fields.
            </p>
            <div className="grid gap-4 sm:grid-cols-2">
              <TextRow label="Company Name" value={profile.companyName} onChange={(v) => setP({ companyName: v })} />
              <TextRow label="Industry" value={profile.companyIndustry} onChange={(v) => setP({ companyIndustry: v })} placeholder="e.g. Software / SaaS" />
              <TextRow label="Website" value={profile.companyWebsite} onChange={(v) => setP({ companyWebsite: v })} placeholder="https://…" />
              <TextRow label="Headquarters / Location" value={profile.companyLocation} onChange={(v) => setP({ companyLocation: v })} placeholder="e.g. Bengaluru, India" />
              <TextRow label="Company Size" value={profile.companySize} onChange={(v) => setP({ companySize: v })} placeholder="e.g. 201–500" />
              <TextRow label="LinkedIn" value={profile.companyLinkedin} onChange={(v) => setP({ companyLinkedin: v })} placeholder="https://linkedin.com/company/…" />
              <TextRow label="Twitter / X" value={profile.companyTwitter} onChange={(v) => setP({ companyTwitter: v })} placeholder="https://x.com/…" />
              <TextRow label="GitHub" value={profile.companyGithub} onChange={(v) => setP({ companyGithub: v })} placeholder="https://github.com/…" />
            </div>
            <div className="mt-4">
              <TextAreaRow label="Description" value={profile.companyDescription} onChange={(v) => setP({ companyDescription: v })} placeholder="What your company does, mission, hiring focus…" />
            </div>
            <SectionActions
              saving={busy("company")}
              onSave={() =>
                saveProfile(
                  {
                    companyName: profile.companyName,
                    companyIndustry: profile.companyIndustry,
                    companyWebsite: profile.companyWebsite,
                    companyLocation: profile.companyLocation,
                    companyDescription: profile.companyDescription,
                    companySize: profile.companySize,
                    companyLinkedin: profile.companyLinkedin,
                    companyTwitter: profile.companyTwitter,
                    companyGithub: profile.companyGithub,
                  },
                  "company",
                )
              }
              onCancel={() => setProfile(profileFrom(recruiter))}
            />
          </DepthCard>
        </TabsContent>
        {/* NOTIFICATIONS */}
        <TabsContent value="notifications">
          <DepthCard className="p-5">
            <div className="mb-3 flex items-center gap-2">
              <Bell className="h-4 w-4 text-violet-bright" />
              <CardTitle>Notification Preferences</CardTitle>
            </div>
            <div>
              <SwitchRow label="New Application" description="A candidate applies to one of your drives." checked={prefs.notifyNewApplication} onChange={(v) => setS({ notifyNewApplication: v })} />
              <SwitchRow label="Candidate Shortlisted" description="A candidate moves to your shortlist." checked={prefs.notifyShortlisted} onChange={(v) => setS({ notifyShortlisted: v })} />
              <SwitchRow label="Interview Updates" description="Interview scheduling and status changes." checked={prefs.notifyInterview} onChange={(v) => setS({ notifyInterview: v })} />
              <SwitchRow label="Offer Updates" description="Offer accepted, declined, or expired." checked={prefs.notifyOffer} onChange={(v) => setS({ notifyOffer: v })} />
              <SwitchRow label="Drive Updates" description="Changes to your placement drives." checked={prefs.notifyDrive} onChange={(v) => setS({ notifyDrive: v })} />
            </div>
            <div className="mt-5 mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">Channels</div>
            <div>
              <SwitchRow label="Email Notifications" description="Receive the above as emails." checked={prefs.emailNotifications} onChange={(v) => setS({ emailNotifications: v })} />
              <SwitchRow label="In-app Notifications" description="Show in the notifications panel." checked={prefs.inAppNotifications} onChange={(v) => setS({ inAppNotifications: v })} />
            </div>
            <SectionActions
              saving={busy("notifications")}
              onSave={() =>
                saveSettings(
                  {
                    notifyNewApplication: prefs.notifyNewApplication,
                    notifyShortlisted: prefs.notifyShortlisted,
                    notifyInterview: prefs.notifyInterview,
                    notifyOffer: prefs.notifyOffer,
                    notifyDrive: prefs.notifyDrive,
                    emailNotifications: prefs.emailNotifications,
                    inAppNotifications: prefs.inAppNotifications,
                  },
                  "notifications",
                )
              }
              onCancel={() => settings && setPrefs(settings)}
            />
          </DepthCard>
        </TabsContent>

        {/* SECURITY */}
        <TabsContent value="security">
          <DepthCard className="mb-5 p-5">
            <div className="mb-4 flex items-center gap-2">
              <KeyRound className="h-4 w-4 text-violet-bright" />
              <CardTitle>Change Password</CardTitle>
            </div>
            <div className="grid gap-4 sm:max-w-md">
              <TextRow label="Current Password" type="password" value={pw.current} onChange={(v) => setPw((p) => ({ ...p, current: v }))} />
              <TextRow label="New Password" type="password" value={pw.next} onChange={(v) => setPw((p) => ({ ...p, next: v }))} hint="At least 8 characters." />
              <TextRow label="Confirm New Password" type="password" value={pw.confirm} onChange={(v) => setPw((p) => ({ ...p, confirm: v }))} />
            </div>
            <div className="mt-5">
              <Button size="sm" variant="glow" onClick={handleChangePassword} disabled={pwSaving || !pw.current || !pw.next}>
                {pwSaving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Check className="h-3.5 w-3.5" />}
                {pwSaving ? "Updating…" : "Update password"}
              </Button>
            </div>
          </DepthCard>

          <DepthCard className="mb-5 p-5">
            <div className="mb-1 flex items-center gap-2">
              <LogOut className="h-4 w-4 text-violet-bright" />
              <CardTitle>Sign out from all devices</CardTitle>
            </div>
            <p className="mb-4 text-xs text-muted-foreground">
              Revokes every active session so other devices must sign in again. Access tokens already issued
              stay valid until they expire (usually minutes); this stops session renewal everywhere.
            </p>
            <Button size="sm" variant="outline" className="text-risk" onClick={handleSignOutEverywhere} disabled={signingOutAll}>
              {signingOutAll ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <LogOut className="h-3.5 w-3.5" />}
              {signingOutAll ? "Signing out…" : "Sign out everywhere"}
            </Button>
          </DepthCard>

          <div className="grid gap-4 sm:grid-cols-3">
            {[
              { icon: ShieldCheck, title: "Two-factor Authentication", body: "Add a second step at sign-in." },
              { icon: Smartphone, title: "Active Sessions", body: "See and manage signed-in devices." },
              { icon: History, title: "Login History", body: "Review recent sign-in activity." },
            ].map((c) => (
              <DepthCard key={c.title} className="p-4 opacity-70">
                <div className="flex items-center gap-2">
                  <c.icon className="h-4 w-4 text-muted-foreground" />
                  <p className="text-sm font-medium">{c.title}</p>
                </div>
                <p className="mt-1 text-xs text-muted-foreground">{c.body}</p>
                <span className="mt-3 inline-flex items-center gap-1 rounded-full bg-muted px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
                  <Clock className="h-3 w-3" /> Coming soon
                </span>
              </DepthCard>
            ))}
          </div>
        </TabsContent>
        {/* PREFERENCES */}
        <TabsContent value="preferences">
          <DepthCard className="p-5">
            <div className="mb-4 flex items-center gap-2">
              <SlidersHorizontal className="h-4 w-4 text-violet-bright" />
              <CardTitle>Preferences</CardTitle>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <SelectRow
                label="Theme"
                value={prefs.theme}
                onChange={(v) => setS({ theme: v })}
                options={[
                  { value: "dark", label: "Dark" },
                  { value: "light", label: "Light" },
                ]}
              />
              <SelectRow
                label="Language"
                value={prefs.language}
                onChange={(v) => setS({ language: v })}
                options={[
                  { value: "en", label: "English" },
                  { value: "hi", label: "हिन्दी (Hindi)" },
                ]}
              />
              <SelectRow
                label="Time Zone"
                value={prefs.timezone}
                onChange={(v) => setS({ timezone: v })}
                options={[
                  { value: "Asia/Kolkata", label: "India (IST)" },
                  { value: "UTC", label: "UTC" },
                  { value: "America/New_York", label: "US Eastern" },
                  { value: "Europe/London", label: "UK (GMT/BST)" },
                ]}
              />
              <SelectRow
                label="Date Format"
                value={prefs.dateFormat}
                onChange={(v) => setS({ dateFormat: v })}
                options={[
                  { value: "DD MMM YYYY", label: "25 Sep 2026" },
                  { value: "MM/DD/YYYY", label: "09/25/2026" },
                  { value: "YYYY-MM-DD", label: "2026-09-25" },
                ]}
              />
            </div>
            <SectionActions
              saving={busy("preferences")}
              onSave={() =>
                saveSettings(
                  { theme: prefs.theme, language: prefs.language, timezone: prefs.timezone, dateFormat: prefs.dateFormat },
                  "preferences",
                )
              }
              onCancel={() => settings && setPrefs(settings)}
            />
          </DepthCard>
        </TabsContent>

        {/* PRIVACY */}
        <TabsContent value="privacy">
          <DepthCard className="p-5">
            <div className="mb-4 flex items-center gap-2">
              <Lock className="h-4 w-4 text-violet-bright" />
              <CardTitle>Privacy</CardTitle>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <SelectRow
                label="Profile Visibility"
                value={prefs.profileVisibility}
                onChange={(v) => setS({ profileVisibility: v })}
                options={[
                  { value: "CAMPUS", label: "Campus only" },
                  { value: "PUBLIC", label: "Public" },
                  { value: "PRIVATE", label: "Private" },
                ]}
              />
              <SelectRow
                label="Recruiter Contact Visibility"
                value={prefs.contactVisibility}
                onChange={(v) => setS({ contactVisibility: v })}
                options={[
                  { value: "CAMPUS", label: "Campus only" },
                  { value: "PUBLIC", label: "Public" },
                  { value: "PRIVATE", label: "Private" },
                ]}
              />
            </div>
            <div className="mt-4">
              <SwitchRow label="Allow usage analytics" description="Help improve the product with anonymized usage data." checked={prefs.allowDataAnalytics} onChange={(v) => setS({ allowDataAnalytics: v })} />
              <SwitchRow label="Marketing emails" description="Occasional product news and tips." checked={prefs.allowMarketingEmail} onChange={(v) => setS({ allowMarketingEmail: v })} />
            </div>
            <SectionActions
              saving={busy("privacy")}
              onSave={() =>
                saveSettings(
                  {
                    profileVisibility: prefs.profileVisibility,
                    contactVisibility: prefs.contactVisibility,
                    allowDataAnalytics: prefs.allowDataAnalytics,
                    allowMarketingEmail: prefs.allowMarketingEmail,
                  },
                  "privacy",
                )
              }
              onCancel={() => settings && setPrefs(settings)}
            />
          </DepthCard>
        </TabsContent>
      </Tabs>
    </div>
  );
}
