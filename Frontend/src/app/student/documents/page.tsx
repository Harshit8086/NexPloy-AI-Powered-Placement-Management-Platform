"use client";

import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { FileText, CheckCircle2, Clock, AlertCircle, Plus, RefreshCw, Upload } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { attachDocumentFile, listDocumentsByStudent, registerDocument } from "@/lib/api/documents";
import { getPrimaryStudent } from "@/lib/api/students";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { documentStatusTone } from "@/lib/status";
import type { DocumentItem, DocumentStatus, Student } from "@/types";

const statusIcon: Record<DocumentStatus, typeof CheckCircle2> = {
  VERIFIED: CheckCircle2,
  "UNDER REVIEW": Clock,
  PENDING: AlertCircle,
  UPLOADED: FileText,
  "NEEDS UPDATE": RefreshCw,
};

/** Document types the backend DocumentType enum accepts. */
const DOCUMENT_TYPES = ["RESUME", "MARKSHEET", "CERTIFICATE", "OFFER_LETTER", "ID_PROOF", "OTHER"];

/** Content types the backend register endpoint allows (SECURITY.md section 3). */
const ALLOWED_CONTENT_TYPES = ["application/pdf", "image/png", "image/jpeg"];

export default function DocumentsPage() {
  // Documents from the API layer: live (GET /api/students/{id}/documents) in live mode, mock otherwise.
  const { data: student } = useApiResource<Student | null>(() => getPrimaryStudent(), [], null);
  const { data: documents, loading, error, reload } = useApiResource<DocumentItem[]>(
    () => (student ? listDocumentsByStudent(student.id) : Promise.resolve([])),
    [student?.id],
    [],
  );
  const pushToast = useAppStore((s) => s.pushToast);

  const [showForm, setShowForm] = useState(false);
  const [docType, setDocType] = useState("MARKSHEET");
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  /** Document id currently receiving a re-upload (legacy metadata-only rows). */
  const [uploadingId, setUploadingId] = useState<string | null>(null);

  const verified = documents.filter((d) => d.status === "VERIFIED").length;
  const awaiting = documents.filter(
    (d) => d.status === "UNDER REVIEW" || d.status === "UPLOADED",
  ).length;
  const actionNeeded = documents.filter(
    (d) => d.status === "PENDING" || d.status === "NEEDS UPDATE",
  ).length;

  /**
   * Two-step upload: registers the document's metadata, then attaches the real file bytes.
   * If the attach fails the row still exists (scan/review pending, no file) and can be
   * completed later via the row's "Upload file" affordance — nothing is lost.
   */
  const submit = async () => {
    if (!student || !file || submitting) return;
    if (!ALLOWED_CONTENT_TYPES.includes(file.type)) {
      pushToast("Unsupported file", "Only PDF, PNG or JPEG files are accepted.");
      return;
    }
    setSubmitting(true);
    try {
      const created = await registerDocument({
        studentId: student.id,
        type: docType,
        fileName: file.name,
        contentType: file.type,
        sizeBytes: file.size,
        storageKey: `students/${student.id}/${file.name}`,
      });
      try {
        await attachDocumentFile(created.id, file);
        pushToast("Document submitted", `${file.name} is uploaded and awaiting scan and review.`);
      } catch {
        pushToast(
          "File not attached",
          "The document was registered but the file upload failed — use Upload file on the row to retry.",
        );
      }
      setShowForm(false);
      setFile(null);
      reload();
    } catch (err) {
      pushToast("Submission failed", err instanceof Error ? err.message : "Unexpected error");
    } finally {
      setSubmitting(false);
    }
  };

  /**
   * Attaches bytes to a metadata-only row (legacy registrations, or a failed second step above).
   * The backend resets scan/review to PENDING on every attach.
   */
  const reupload = async (documentId: string, picked: File) => {
    if (!ALLOWED_CONTENT_TYPES.includes(picked.type)) {
      pushToast("Unsupported file", "Only PDF, PNG or JPEG files are accepted.");
      return;
    }
    setUploadingId(documentId);
    try {
      await attachDocumentFile(documentId, picked);
      pushToast("File uploaded", `${picked.name} is attached and awaiting scan and review.`);
      reload();
    } catch (err) {
      pushToast("Upload failed", err instanceof Error ? err.message : "Unexpected error");
    } finally {
      setUploadingId(null);
    }
  };

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        eyebrow="Profile"
        title="Documents"
        subtitle="Verification status for every document in your placement file."
      />

      <div className="mb-5 grid grid-cols-2 gap-4 sm:grid-cols-3">
        <StatCard label="Verified" value={verified} icon={CheckCircle2} accent="success" />
        <StatCard label="Awaiting Review" value={awaiting} icon={Clock} accent="warning" />
        <StatCard label="Action Needed" value={actionNeeded} icon={AlertCircle} accent="risk" />
      </div>

      <div className="mb-4 flex justify-end">
        <Button variant="outline" size="sm" onClick={() => setShowForm((s) => !s)}>
          {showForm ? (
            "Cancel"
          ) : (
            <>
              <Plus className="h-3.5 w-3.5" /> Add document
            </>
          )}
        </Button>
      </div>

      {showForm && student && (
        <Card className="mb-4">
          <div className="flex flex-wrap items-end gap-3">
            <div className="w-44">
              <p className="mb-1 text-xs font-medium text-muted-foreground">Document type</p>
              <Select value={docType} onValueChange={setDocType}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {DOCUMENT_TYPES.map((t) => (
                    <SelectItem key={t} value={t}>
                      {t.replace(/_/g, " ")}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div>
              <p className="mb-1 text-xs font-medium text-muted-foreground">
                File (PDF, PNG or JPEG)
              </p>
              <input
                type="file"
                accept=".pdf,.png,.jpg,.jpeg"
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                className="text-xs file:mr-2 file:rounded-md file:border-0 file:bg-muted file:px-3 file:py-1.5 file:text-xs file:font-medium"
              />
            </div>
            <Button size="sm" disabled={!file || submitting} onClick={submit}>
              {submitting ? (
                <>
                  <RefreshCw className="h-3.5 w-3.5 animate-spin" /> Submitting...
                </>
              ) : (
                <>
                  <Upload className="h-3.5 w-3.5" /> Submit
                </>
              )}
            </Button>
          </div>
        </Card>
      )}

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={documents.length === 0}
        onRetry={reload}
        emptyTitle="No documents yet"
        emptyMessage="Add your first document — the placement office scans and verifies it before offers move ahead."
      >
        <Card className="divide-y divide-border p-0">
          {documents.map((d) => {
          const Icon = statusIcon[d.status];
          return (
            <div key={d.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
              <div className="flex min-w-0 items-center gap-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-muted">
                  <AnimatePresence mode="wait" initial={false}>
                    <motion.span
                      key={d.status}
                      initial={{ opacity: 0, scale: 0.7 }}
                      animate={{ opacity: 1, scale: 1 }}
                      exit={{ opacity: 0, scale: 0.7 }}
                      transition={{ duration: 0.2 }}
                    >
                      <Icon
                        className={
                          d.status === "VERIFIED"
                            ? "h-4 w-4 text-success"
                            : "h-4 w-4 text-muted-foreground"
                        }
                      />
                    </motion.span>
                  </AnimatePresence>
                </div>
                <div className="min-w-0">
                  <p className="text-sm font-medium">{d.name}</p>
                  <p className="text-xs text-muted-foreground">
                    {d.note ?? `Updated ${d.updatedOn}`}
                  </p>
                  {!d.hasFile && (
                    <p className="mt-0.5 text-[11px] text-warning">
                      No file attached — upload it so the placement office can review it.
                    </p>
                  )}
                  {d.status === "NEEDS UPDATE" && (
                    <p className="mt-0.5 text-[11px] text-risk">
                      The placement office rejected this document — upload a corrected file.
                    </p>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-2">
                {(!d.hasFile || d.status === "NEEDS UPDATE") && (
                  <label className="cursor-pointer">
                    <span className="sr-only">Upload file for {d.name}</span>
                    <input
                      type="file"
                      accept=".pdf,.png,.jpg,.jpeg"
                      className="hidden"
                      disabled={uploadingId === d.id}
                      onChange={(e) => {
                        const picked = e.target.files?.[0];
                        e.target.value = "";
                        if (picked) reupload(d.id, picked);
                      }}
                    />
                    <span className="inline-flex items-center gap-1.5 rounded-md border border-border px-2.5 py-1 text-xs font-medium hover:bg-muted">
                      {uploadingId === d.id ? (
                        <RefreshCw className="h-3 w-3 animate-spin" />
                      ) : (
                        <Upload className="h-3 w-3" />
                      )}
                      {!d.hasFile ? "Upload file" : "Re-upload file"}
                    </span>
                  </label>
                )}
                <AnimatePresence mode="wait" initial={false}>
                  <motion.div
                    key={d.status}
                    initial={{ opacity: 0, y: -4 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: 4 }}
                    transition={{ duration: 0.2 }}
                  >
                    <Badge variant={documentStatusTone[d.status]}>{d.status}</Badge>
                  </motion.div>
                </AnimatePresence>
              </div>
            </div>
          );
        })}
        </Card>
      </AsyncBoundary>

      <p className="mt-3 text-xs text-muted-foreground">
        The placement office scans and reviews each submission — only approved documents unlock the
        offer&apos;s document-verification step. Re-uploading a file restarts scan and review.
      </p>
    </div>
  );
}