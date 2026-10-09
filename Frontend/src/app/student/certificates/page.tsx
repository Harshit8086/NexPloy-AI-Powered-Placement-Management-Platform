"use client";

import { useMemo, useRef, useState } from "react";
import {
  Award,
  BadgeCheck,
  Building2,
  ExternalLink,
  Eye,
  FileText,
  Loader2,
  Pencil,
  Plus,
  Trash2,
  Upload,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { PageHeader } from "@/components/layout/PageHeader";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import {
  CERTIFICATE_TYPES,
  certificateTypeLabel,
  certificateHasFile,
  createCertificate,
  deleteCertificate,
  fetchCertificateFile,
  listCertificates,
  updateCertificateMetadata,
  replaceCertificateFile,
  type CertificateInput,
  type CertificateType,
  type StudentCertificate,
} from "@/lib/api/certificates";

const EMPTY_FORM: CertificateInput = {
  title: "",
  type: "COURSE_COMPLETION",
  issuingOrganization: "",
  description: "",
  credentialId: "",
  verificationUrl: "",
  issuerName: "",
  organizationWebsite: "",
};

/** ".pdf, 240 KB" style caption for a stored document. */
function fileMeta(cert: StudentCertificate): string {
  const parts: string[] = [];
  if (cert.fileName) parts.push(cert.fileName);
  if (cert.fileSizeBytes != null) parts.push(`${Math.max(1, Math.round(cert.fileSizeBytes / 1024))} KB`);
  return parts.join(" · ");
}

export default function CertificatesPage() {
  const pushToast = useAppStore((s) => s.pushToast);

  const [version, setVersion] = useState(0);
  const { data: certificates, loading, error } = useApiResource<StudentCertificate[]>(
    () => listCertificates(),
    [version],
    [],
  );
  const refetch = () => setVersion((v) => v + 1);

  // Dialog + form state. editingId === null means "add".
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<CertificateInput>(EMPTY_FORM);
  const [file, setFile] = useState<File | null>(null);
  const [existingFileName, setExistingFileName] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [viewingId, setViewingId] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const setField = (key: keyof CertificateInput, value: string) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const openAdd = () => {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setFile(null);
    setExistingFileName(null);
    setFormError(null);
    setDialogOpen(true);
  };

  const openEdit = (cert: StudentCertificate) => {
    setEditingId(cert.id);
    setForm({
      title: cert.title,
      type: cert.type,
      issuingOrganization: cert.issuingOrganization,
      description: cert.description ?? "",
      credentialId: cert.credentialId ?? "",
      verificationUrl: cert.verificationUrl ?? "",
      issuerName: cert.issuerName ?? "",
      organizationWebsite: cert.organizationWebsite ?? "",
    });
    setFile(null);
    setExistingFileName(cert.fileName);
    setFormError(null);
    setDialogOpen(true);
  };

  const onPickFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFile(e.target.files?.[0] ?? null);
    e.target.value = ""; // allow re-selecting the same file
  };

  const submit = async () => {
    if (!form.title.trim() || !form.issuingOrganization.trim()) {
      setFormError("Title and issuing organization are required.");
      return;
    }
    if (editingId === null && !file) {
      setFormError("A certificate document (PDF, PNG or JPEG) is required.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      if (editingId === null) {
        await createCertificate(form, file as File);
        pushToast("Certificate added", `${form.title} was added to your profile.`);
      } else {
        await updateCertificateMetadata(editingId, form);
        if (file) await replaceCertificateFile(editingId, file);
        pushToast("Certificate updated", `${form.title} was updated.`);
      }
      setDialogOpen(false);
      refetch();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Could not save the certificate.");
    } finally {
      setSaving(false);
    }
  };

  const remove = async (cert: StudentCertificate) => {
    try {
      await deleteCertificate(cert.id);
      pushToast("Certificate removed", `${cert.title} was deleted.`);
      refetch();
    } catch (err) {
      pushToast("Delete failed", err instanceof Error ? err.message : "Could not delete the certificate.");
    }
  };

  const viewFile = async (cert: StudentCertificate) => {
    setViewingId(cert.id);
    try {
      const blob = await fetchCertificateFile(cert.id);
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank", "noopener");
      // Give the new tab time to load before releasing the object URL.
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      pushToast("Could not open document", err instanceof Error ? err.message : "The file could not be loaded.");
    } finally {
      setViewingId(null);
    }
  };

  const count = certificates.length;
  const dialogTitle = editingId === null ? "Add certificate" : "Edit certificate";

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        eyebrow="Profile"
        title="Certificates"
        subtitle="Add and showcase the certificates you've earned. They appear on your profile."
        actions={
          <Button variant="glow" onClick={openAdd}>
            <Plus className="h-4 w-4" /> Add Certificate
          </Button>
        }
      />

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={count === 0}
        onRetry={refetch}
        emptyTitle="No certificates yet"
        emptyMessage="Add your first certificate to showcase it on your profile."
      >
        <div className="grid gap-4 md:grid-cols-2">
          {certificates.map((cert) => (
            <CertificateCard
              key={cert.id}
              cert={cert}
              viewing={viewingId === cert.id}
              onView={() => viewFile(cert)}
              onEdit={() => openEdit(cert)}
              onDelete={() => remove(cert)}
            />
          ))}
        </div>
      </AsyncBoundary>

      <Dialog open={dialogOpen} onOpenChange={(open) => !saving && setDialogOpen(open)}>
        <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{dialogTitle}</DialogTitle>
            <DialogDescription>
              Required: title, type, issuing organization and a document file.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-3">
            <Field label="Certificate title" required>
              <Input
                value={form.title}
                onChange={(e) => setField("title", e.target.value)}
                placeholder="e.g. AWS Certified Cloud Practitioner"
              />
            </Field>

            <Field label="Certificate type" required>
              <Select value={form.type} onValueChange={(v) => setField("type", v as CertificateType)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {CERTIFICATE_TYPES.map((t) => (
                    <SelectItem key={t.value} value={t.value}>
                      {t.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>

            <Field label="Issuing organization" required>
              <Input
                value={form.issuingOrganization}
                onChange={(e) => setField("issuingOrganization", e.target.value)}
                placeholder="e.g. Amazon Web Services"
              />
            </Field>

            <Field label="Description">
              <textarea
                value={form.description}
                onChange={(e) => setField("description", e.target.value)}
                rows={3}
                placeholder="Optional — what this certificate covers."
                className="flex w-full rounded-lg border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              />
            </Field>

            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Credential ID">
                <Input
                  value={form.credentialId}
                  onChange={(e) => setField("credentialId", e.target.value)}
                  placeholder="Optional"
                />
              </Field>
              <Field label="Verification URL">
                <Input
                  value={form.verificationUrl}
                  onChange={(e) => setField("verificationUrl", e.target.value)}
                  placeholder="https://…"
                />
              </Field>
              <Field label="Issuer name">
                <Input
                  value={form.issuerName}
                  onChange={(e) => setField("issuerName", e.target.value)}
                  placeholder="Optional"
                />
              </Field>
              <Field label="Organization website">
                <Input
                  value={form.organizationWebsite}
                  onChange={(e) => setField("organizationWebsite", e.target.value)}
                  placeholder="https://…"
                />
              </Field>
            </div>

            <Field label={editingId === null ? "Certificate document" : "Replace document (optional)"} required={editingId === null}>
              <div className="flex flex-wrap items-center gap-2">
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
                  className="hidden"
                  onChange={onPickFile}
                />
                <Button type="button" variant="outline" size="sm" onClick={() => fileInputRef.current?.click()}>
                  <Upload className="h-3.5 w-3.5" /> Choose file
                </Button>
                <span className="min-w-0 truncate text-xs text-muted-foreground">
                  {file?.name ?? (existingFileName ? `Current: ${existingFileName}` : "No file selected")}
                </span>
              </div>
              <p className="mt-1 text-[11px] text-muted-foreground">PDF, PNG or JPEG · up to 10 MB.</p>
            </Field>

            {formError && (
              <p className="text-xs text-risk" role="alert">
                {formError}
              </p>
            )}
          </div>

          <DialogFooter>
            <Button variant="ghost" onClick={() => setDialogOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button variant="glow" onClick={submit} disabled={saving}>
              {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <BadgeCheck className="h-4 w-4" />}
              {editingId === null ? "Add certificate" : "Save changes"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Field({
  label,
  required,
  children,
}: {
  label: string;
  required?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div>
      <Label className="mb-1 block text-xs">
        {label} {required && <span className="text-risk">*</span>}
      </Label>
      {children}
    </div>
  );
}

function CertificateCard({
  cert,
  viewing,
  onView,
  onEdit,
  onDelete,
}: {
  cert: StudentCertificate;
  viewing: boolean;
  onView: () => void;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const meta = useMemo(() => fileMeta(cert), [cert]);
  return (
    <DepthCard className="flex h-full flex-col p-5">
      <div className="flex items-start gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-violet/10 text-violet-bright">
          <Award className="h-5 w-5" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate font-semibold">{cert.title}</p>
          <p className="flex items-center gap-1.5 truncate text-xs text-muted-foreground">
            <Building2 className="h-3.5 w-3.5 shrink-0" /> {cert.issuingOrganization}
          </p>
        </div>
        <Badge variant="muted" className="shrink-0">
          {certificateTypeLabel(cert.type)}
        </Badge>
      </div>

      {cert.description && (
        <p className="mt-3 line-clamp-3 text-sm text-muted-foreground">{cert.description}</p>
      )}

      <div className="mt-3 space-y-1 text-xs text-muted-foreground">
        {cert.credentialId && (
          <p className="truncate">
            <span className="text-foreground/70">Credential ID:</span> {cert.credentialId}
          </p>
        )}
        {cert.issuerName && (
          <p className="truncate">
            <span className="text-foreground/70">Issuer:</span> {cert.issuerName}
          </p>
        )}
        {meta && (
          <p className="flex items-center gap-1.5 truncate">
            <FileText className="h-3.5 w-3.5 shrink-0" /> {meta}
          </p>
        )}
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-border pt-3">
        {certificateHasFile(cert) && (
          <Button variant="outline" size="sm" onClick={onView} disabled={viewing}>
            {viewing ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Eye className="h-3.5 w-3.5" />}
            View
          </Button>
        )}
        {cert.verificationUrl && (
          <Button asChild variant="ghost" size="sm">
            <a href={cert.verificationUrl} target="_blank" rel="noopener noreferrer">
              Verify <ExternalLink className="h-3.5 w-3.5" />
            </a>
          </Button>
        )}
        <div className="ml-auto flex items-center gap-1.5">
          <Button variant="ghost" size="sm" onClick={onEdit} aria-label={`Edit ${cert.title}`}>
            <Pencil className="h-3.5 w-3.5" />
          </Button>
          <Button variant="ghost" size="sm" onClick={onDelete} aria-label={`Delete ${cert.title}`}>
            <Trash2 className="h-3.5 w-3.5" />
          </Button>
        </div>
      </div>
    </DepthCard>
  );
}
