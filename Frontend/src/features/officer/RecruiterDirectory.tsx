"use client";

import { useMemo, useState } from "react";
import { Search } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { PageHeader } from "@/components/layout/PageHeader";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { listRecruiters, adminUpdateRecruiter, setRecruiterActive } from "@/lib/api/recruiters";
import type { RecruiterUpdateInput } from "@/lib/api/recruiters";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import type { Recruiter } from "@/types";

/** Bounded, commonly-meaningful subset editable from the officer directory. */
type EditForm = {
  name: string;
  phone: string;
  designation: string;
  companyName: string;
  companyIndustry: string;
  companyWebsite: string;
  companyLocation: string;
  companySize: string;
};

const EMPTY_FORM: EditForm = {
  name: "",
  phone: "",
  designation: "",
  companyName: "",
  companyIndustry: "",
  companyWebsite: "",
  companyLocation: "",
  companySize: "",
};

export function RecruiterDirectory() {
  // listRecruiters() is campus-scoped server-side (from the auth principal), so it takes no
  // campusId. We still key the fetch on activeCampusId so switching campus reloads the roster.
  const campusId = useAppStore((s) => s.activeCampusId);
  const pushToast = useAppStore((s) => s.pushToast);

  const { data: recruiters, loading, error, reload } = useApiResource<Recruiter[]>(
    () => listRecruiters(),
    [campusId],
    [],
  );

  const [query, setQuery] = useState("");
  const [editing, setEditing] = useState<Recruiter | null>(null);
  const [deactivateTarget, setDeactivateTarget] = useState<Recruiter | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState<EditForm>(EMPTY_FORM);

  const filtered = useMemo(
    () =>
      recruiters.filter((r) =>
        query
          ? `${r.name} ${r.companyName} ${r.designation} ${r.industry} ${r.hqLocation}`
              .toLowerCase()
              .includes(query.toLowerCase())
          : true,
      ),
    [recruiters, query],
  );

  const setField = (key: keyof EditForm) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }));

  const openEdit = (r: Recruiter) => {
    setForm({
      name: r.name,
      phone: r.phone,
      designation: r.designation,
      companyName: r.companyName,
      // Read type flattens these (industry / website / hqLocation); update input uses company* keys.
      companyIndustry: r.industry,
      companyWebsite: r.website,
      companyLocation: r.hqLocation,
      companySize: r.companySize,
    });
    setEditing(r);
  };

  const submit = async () => {
    if (!editing) return;
    if (!form.name.trim() || !form.companyName.trim()) {
      pushToast("Missing details", "Contact name and company name are required.");
      return;
    }
    setSubmitting(true);
    try {
      const input: RecruiterUpdateInput = {
        name: form.name.trim(),
        phone: form.phone.trim(),
        designation: form.designation.trim(),
        companyName: form.companyName.trim(),
        companyIndustry: form.companyIndustry.trim(),
        companyWebsite: form.companyWebsite.trim(),
        companyLocation: form.companyLocation.trim(),
        companySize: form.companySize.trim(),
      };
      await adminUpdateRecruiter(editing.id, input);
      pushToast("Recruiter updated", `${input.companyName} saved.`);
      reload();
      setEditing(null);
    } catch (err) {
      pushToast("Save failed", err instanceof Error ? err.message : "Could not save the recruiter.");
    } finally {
      setSubmitting(false);
    }
  };

  const setActive = async (r: Recruiter, active: boolean) => {
    setSubmitting(true);
    try {
      await setRecruiterActive(r.id, active);
      pushToast(
        active ? "Recruiter reactivated" : "Recruiter deactivated",
        active
          ? `${r.companyName} can access the platform again.`
          : `${r.companyName} can no longer sign in; their drives and history are preserved.`,
      );
      reload();
      setDeactivateTarget(null);
    } catch (err) {
      pushToast(
        "Update failed",
        err instanceof Error ? err.message : "Could not change the recruiter's status.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader
        eyebrow="Placement"
        title="Recruiters"
        subtitle="Manage the campus's registered recruiters — edit company profiles or disable access."
      />

      <div className="mb-4 mt-5 flex flex-wrap items-center gap-3">
        <div className="relative min-w-[200px] flex-1">
          <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search company, contact, industry or location..."
            className="pl-8"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Search recruiters"
          />
        </div>
      </div>

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={recruiters.length === 0}
        onRetry={reload}
        emptyTitle="No recruiters yet"
        emptyMessage="Recruiters appear here once they register for this campus."
      >
        <Card className="overflow-x-auto p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Company</TableHead>
                <TableHead>Contact</TableHead>
                <TableHead>Industry / Location</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((r) => (
                <TableRow key={r.id}>
                  <TableCell>
                    <div className="flex items-center gap-2.5">
                      <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-violet/10 text-xs font-semibold text-violet-bright">
                        {r.logoInitial}
                      </div>
                      <span className="font-medium">{r.companyName}</span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <span className="block">{r.name || "—"}</span>
                    <span className="block text-[10px] text-muted-foreground">
                      {r.designation || "—"}
                    </span>
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {r.industry || "—"}
                    {r.hqLocation && ` · ${r.hqLocation}`}
                  </TableCell>
                  <TableCell>
                    <Badge variant={r.active ? "success" : "muted"}>
                      {r.active ? "Active" : "Inactive"}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-2">
                      <Button variant="outline" size="sm" onClick={() => openEdit(r)}>
                        Edit
                      </Button>
                      {r.active ? (
                        <Button
                          variant="outline"
                          size="sm"
                          className="text-risk"
                          onClick={() => setDeactivateTarget(r)}
                        >
                          Deactivate
                        </Button>
                      ) : (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setActive(r, true)}
                          disabled={submitting}
                        >
                          Activate
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
              {filtered.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} className="py-10 text-center text-sm text-muted-foreground">
                    {loading ? "Loading recruiters…" : "No recruiters match your search."}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Card>
      </AsyncBoundary>

      {/* Edit */}
      <Dialog open={!!editing} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Edit Recruiter</DialogTitle>
            <DialogDescription>
              Update the recruiter&apos;s contact and company details.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="rec-name">
                  Contact Name <span className="text-risk">*</span>
                </Label>
                <Input id="rec-name" value={form.name} onChange={setField("name")} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="rec-designation">Designation</Label>
                <Input
                  id="rec-designation"
                  value={form.designation}
                  onChange={setField("designation")}
                />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="rec-phone">Phone</Label>
              <Input id="rec-phone" value={form.phone} onChange={setField("phone")} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="rec-company">
                Company Name <span className="text-risk">*</span>
              </Label>
              <Input id="rec-company" value={form.companyName} onChange={setField("companyName")} />
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="rec-industry">Industry</Label>
                <Input
                  id="rec-industry"
                  value={form.companyIndustry}
                  onChange={setField("companyIndustry")}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="rec-size">Company Size</Label>
                <Input id="rec-size" value={form.companySize} onChange={setField("companySize")} />
              </div>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="rec-location">Location</Label>
                <Input
                  id="rec-location"
                  value={form.companyLocation}
                  onChange={setField("companyLocation")}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="rec-website">Website</Label>
                <Input
                  id="rec-website"
                  value={form.companyWebsite}
                  onChange={setField("companyWebsite")}
                />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditing(null)} disabled={submitting}>
              Cancel
            </Button>
            <Button variant="glow" onClick={submit} disabled={submitting}>
              {submitting ? "Saving…" : "Save Changes"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Deactivate confirmation */}
      <Dialog open={!!deactivateTarget} onOpenChange={(open) => !open && setDeactivateTarget(null)}>
        <DialogContent className="max-w-md">
          {deactivateTarget && (
            <>
              <DialogHeader>
                <DialogTitle>Deactivate recruiter?</DialogTitle>
                <DialogDescription>
                  This disables{" "}
                  <span className="font-medium text-foreground">{deactivateTarget.companyName}</span>
                  &apos;s access — they can no longer sign in or post drives. Their existing drives,
                  applications and hiring history are preserved and can be restored by reactivating
                  them later.
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button
                  variant="outline"
                  onClick={() => setDeactivateTarget(null)}
                  disabled={submitting}
                >
                  Cancel
                </Button>
                <Button
                  variant="outline"
                  className="text-risk"
                  onClick={() => setActive(deactivateTarget, false)}
                  disabled={submitting}
                >
                  {submitting ? "Deactivating…" : "Deactivate"}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
