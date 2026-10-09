"use client";

import { useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  AlertCircle,
  BadgeCheck,
  CheckCircle2,
  Clock,
  Eye,
  FileText,
  Loader2,
  RefreshCw,
  Search,
  XCircle,
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import {
  fetchDocumentFile,
  listDocuments,
  recordDocumentScan,
  reviewDocument,
} from "@/lib/api/documents";
import { listStudentsByCampus } from "@/lib/api/students";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { documentStatusTone } from "@/lib/status";
import { cn } from "@/lib/utils";
import type { DocumentItem, DocumentStatus, Student } from "@/types";

const statusIcon: Record<DocumentStatus, typeof CheckCircle2> = {
  VERIFIED: CheckCircle2,
  "UNDER REVIEW": Clock,
  PENDING: AlertCircle,
  UPLOADED: FileText,
  "NEEDS UPDATE": RefreshCw,
};

/** A document row joined to its student for display. */
type DocumentRow = DocumentItem & { student?: Student };

/**
 * Preview-and-decide dialog: fetches the stored file bytes on open and renders them inline
 * (PDF in an iframe, images in an img tag), with Approve/Reject in the footer so the officer
 * never leaves the screen between looking and deciding. A scan-pending document is cleared
 * (scan CLEAN) before the review call, mirroring the backend's APPROVED-requires-CLEAN rule.
 */
function DocumentReviewDialog({
  row,
  onClose,
  onDecided,
}: {
  row: DocumentRow | null;
  onClose: () => void;
  onDecided: () => void;
}) {
  const pushToast = useAppStore((s) => s.pushToast);
  const [preview, setPreview] = useState<{ url: string; contentType: string } | null>(null);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState<string | null>(null);
  const [deciding, setDeciding] = useState<"APPROVED" | "REJECTED" | null>(null);

  useEffect(() => {
    if (!row) return;
    let objectUrl: string | null = null;
    let cancelled = false;
    setLoading(true);
    setFailed(null);
    setPreview(null);
    fetchDocumentFile(row.id)
      .then((blob) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setPreview({ url: objectUrl, contentType: blob.type || row.contentType || "application/pdf" });
      })
      .catch((err) => {
        if (!cancelled) setFailed(err instanceof Error ? err.message : "Could not load the file.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [row]);

  /**
   * Approves or rejects the open document. A scan-pending document is first cleared (scan
   * CLEAN), then reviewed — the backend refuses approval until the scan is clean. Once
   * approved, the offer funnel's verify-documents step can proceed.
   */
  const decide = async (decision: "APPROVED" | "REJECTED") => {
    if (!row || deciding) return;
    setDeciding(decision);
    try {
      if (row.status === "PENDING") {
        await recordDocumentScan(row.id, "CLEAN");
      }
      await reviewDocument(row.id, decision);
      pushToast(
        decision === "APPROVED" ? "Document verified" : "Document rejected",
        `${row.student?.name ?? "The student"}'s ${row.name} is now ${decision}.`,
      );
      onDecided();
      onClose();
    } catch (err) {
      pushToast("Review failed", err instanceof Error ? err.message : "Unexpected error");
    } finally {
      setDeciding(null);
    }
  };

  return (
    <Dialog open={row !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <FileText className="h-4 w-4 text-brand" />
            {row?.name}
          </DialogTitle>
          <DialogDescription>
            {row ? `${row.student?.name ?? "Unknown student"} — review the document, then approve or reject it.` : ""}
          </DialogDescription>
        </DialogHeader>
        {loading && (
          <div className="flex h-40 items-center justify-center text-sm text-muted-foreground">
            <Loader2 className="mr-2 h-4 w-4 animate-spin" /> Loading document...
          </div>
        )}
        {failed && (
          <div className="flex h-40 items-center justify-center text-sm text-risk">
            {failed}
          </div>
        )}
        {preview &&
          (preview.contentType === "application/pdf" ? (
            <iframe
              src={preview.url}
              title="Document preview"
              className="h-[60vh] w-full rounded-lg border border-border bg-white"
            />
          ) : (
            <div className="flex justify-center">
              {/* Blob object URLs cannot go through next/image optimization. */}
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={preview.url}
                alt="Document preview"
                className="max-h-[60vh] rounded-lg object-contain"
              />
            </div>
          ))}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Close
          </Button>
          <Button
            variant="destructive"
            disabled={deciding !== null || loading}
            onClick={() => decide("REJECTED")}
          >
            {deciding === "REJECTED" ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <XCircle className="h-3.5 w-3.5" />
            )}
            Reject
          </Button>
          <Button disabled={deciding !== null || loading} onClick={() => decide("APPROVED")}>
            {deciding === "APPROVED" ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <BadgeCheck className="h-3.5 w-3.5" />
            )}
            Approve
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default function OfficerDocumentsPage() {
  const campusId = useAppStore((s) => s.activeCampusId);
  const [query, setQuery] = useState("");
  const [previewing, setPreviewing] = useState<DocumentRow | null>(null);

  // Live: documents (campus-scoped server-side) joined to the campus roster for
  // the student name/avatar. No mock fallback — an empty campus yields no rows.
  const { data, loading, error, reload } = useApiResource(
    async () => {
      const [docs, students] = await Promise.all([
        listDocuments(),
        listStudentsByCampus(campusId),
      ]);
      const byId = new Map(students.map((s) => [s.id, s]));
      return docs
        .map((d) => ({ ...d, student: byId.get(d.studentId) }))
        .filter((d) => d.student !== undefined);
    },
    [campusId],
    [],
  );

  const rows = data.filter((d) =>
    query ? `${d.student?.name} ${d.name}`.toLowerCase().includes(query.toLowerCase()) : true,
  );

  const verified = rows.filter((d) => d.status === "VERIFIED").length;
  const underReview = rows.filter(
    (d) => d.status === "UNDER REVIEW" || d.status === "UPLOADED",
  ).length;
  const pending = rows.filter(
    (d) => d.status === "PENDING" || d.status === "NEEDS UPDATE",
  ).length;
  const complianceRate = rows.length ? Math.round((verified / rows.length) * 100) : 0;

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        eyebrow="Operations"
        title="Document Tracking"
        subtitle="Verification compliance across the roster — open a submitted document, review it, then approve or reject."
      />

      <div className="mb-5 grid grid-cols-2 gap-4 sm:grid-cols-4">
        <StatCard label="Verified" value={verified} icon={CheckCircle2} accent="success" />
        <StatCard label="Awaiting Review" value={underReview} icon={Clock} accent="warning" />
        <StatCard label="Action Needed" value={pending} icon={AlertCircle} accent="risk" />
        <StatCard label="Compliance" value={complianceRate} suffix="%" icon={BadgeCheck} accent="violet" />
      </div>

      <div className="relative mb-4 max-w-sm">
        <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder="Search student or document..."
          className="pl-8"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Search documents"
        />
      </div>

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={data.length === 0}
        onRetry={reload}
        emptyTitle="No documents tracked"
        emptyMessage="No documents have been submitted for this campus yet."
      >
      <Card className="overflow-x-auto p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Student</TableHead>
              <TableHead>Document</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Updated</TableHead>
              <TableHead className="text-right">Action</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((d) => {
              const Icon = statusIcon[d.status];
              return (
                <TableRow key={d.id} className={cn(d.status === "NEEDS UPDATE" && "bg-risk/5")}>
                  <TableCell>
                    <div className="flex items-center gap-2.5">
                      <Avatar className="h-7 w-7">
                        <AvatarFallback className="text-[10px]">
                          {d.student?.avatarInitials}
                        </AvatarFallback>
                      </Avatar>
                      <span className="font-medium">{d.student?.name}</span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <span className="flex items-center gap-1.5 text-muted-foreground">
                      <Icon className="h-3.5 w-3.5" /> {d.name}
                    </span>
                    {d.note && <p className="mt-0.5 text-[11px] text-risk">{d.note}</p>}
                  </TableCell>
                  <TableCell>
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
                  </TableCell>
                  <TableCell className="text-muted-foreground">{d.updatedOn}</TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={!d.hasFile}
                      onClick={() => setPreviewing(d)}
                      title={d.hasFile ? "View and review the document" : "No file attached yet"}
                    >
                      <Eye className="h-3.5 w-3.5" /> View
                    </Button>
                    {!d.hasFile && (
                      <p className="mt-1 text-[11px] text-muted-foreground">
                        No file — awaiting student upload
                      </p>
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
            {rows.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} className="py-10 text-center text-sm text-muted-foreground">
                  No documents tracked for this campus.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Card>
      </AsyncBoundary>

      <DocumentReviewDialog
        row={previewing}
        onClose={() => setPreviewing(null)}
        onDecided={reload}
      />

      <p className="mt-3 text-xs text-muted-foreground">
        Document records are live from the server. Approving records a CLEAN scan and an APPROVED
        review, and persists — approved documents unlock the offer funnel&apos;s document-verification
        step. A student re-upload resets a document back to scan/review pending.
      </p>
    </div>
  );
}