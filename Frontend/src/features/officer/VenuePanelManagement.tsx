"use client";

import { useState } from "react";
import { Plus, MapPin, Users, X } from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
import { DepthCard } from "@/components/intelligence/DepthCard";
import {
  listVenues,
  listPanels,
  createVenue,
  updateVenue,
  setVenueActive,
  createPanel,
  updatePanel,
  setPanelActive,
  type PanelMemberInput,
} from "@/lib/api/schedules";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import type { SchedulePanel, ScheduleVenue } from "@/types";

interface Data {
  venues: ScheduleVenue[];
  panels: SchedulePanel[];
}

type ActiveTarget =
  | { kind: "venue"; entity: ScheduleVenue }
  | { kind: "panel"; entity: SchedulePanel };

export function VenuePanelManagement() {
  const pushToast = useAppStore((s) => s.pushToast);
  const { data, loading, error, reload } = useApiResource<Data>(
    async () => {
      const [venues, panels] = await Promise.all([listVenues(), listPanels()]);
      return { venues, panels };
    },
    [],
    { venues: [], panels: [] },
  );

  const [submitting, setSubmitting] = useState(false);
  const [deactivating, setDeactivating] = useState<ActiveTarget | null>(null);

  // Venue form
  const [venueOpen, setVenueOpen] = useState(false);
  const [editingVenue, setEditingVenue] = useState<ScheduleVenue | null>(null);
  const [venueName, setVenueName] = useState("");
  const [venueCapacity, setVenueCapacity] = useState("");

  // Panel form
  const [panelOpen, setPanelOpen] = useState(false);
  const [editingPanel, setEditingPanel] = useState<SchedulePanel | null>(null);
  const [panelName, setPanelName] = useState("");
  const [members, setMembers] = useState<PanelMemberInput[]>([]);

  const openCreateVenue = () => {
    setEditingVenue(null);
    setVenueName("");
    setVenueCapacity("");
    setVenueOpen(true);
  };

  const openEditVenue = (v: ScheduleVenue) => {
    setEditingVenue(v);
    setVenueName(v.name);
    setVenueCapacity(v.capacity != null ? String(v.capacity) : "");
    setVenueOpen(true);
  };

  const submitVenue = async () => {
    if (!venueName.trim()) {
      pushToast("Missing details", "Venue name is required.");
      return;
    }
    const capacity = venueCapacity.trim() ? Number(venueCapacity) : null;
    if (capacity != null && !(capacity >= 1)) {
      pushToast("Invalid capacity", "Capacity must be a positive number.");
      return;
    }
    setSubmitting(true);
    try {
      if (editingVenue) {
        await updateVenue(editingVenue.id, { name: venueName.trim(), capacity });
        pushToast("Venue updated", `${venueName.trim()} saved.`);
      } else {
        await createVenue({ name: venueName.trim(), capacity });
        pushToast("Venue created", `${venueName.trim()} added.`);
      }
      reload();
      setVenueOpen(false);
      setEditingVenue(null);
    } catch (err) {
      pushToast("Save failed", err instanceof Error ? err.message : "Could not save the venue.");
    } finally {
      setSubmitting(false);
    }
  };

  const openCreatePanel = () => {
    setEditingPanel(null);
    setPanelName("");
    setMembers([{ name: "", email: "" }]);
    setPanelOpen(true);
  };

  const openEditPanel = (p: SchedulePanel) => {
    setEditingPanel(p);
    setPanelName(p.name);
    setMembers(p.members.map((m) => ({ name: m.name, email: m.email })));
    setPanelOpen(true);
  };

  const submitPanel = async () => {
    if (!panelName.trim()) {
      pushToast("Missing details", "Panel name is required.");
      return;
    }
    const roster = members
      .map((m) => ({ name: m.name.trim(), email: (m.email ?? "").trim() || null }))
      .filter((m) => m.name.length > 0);
    setSubmitting(true);
    try {
      if (editingPanel) {
        await updatePanel(editingPanel.id, { name: panelName.trim(), members: roster });
        pushToast("Panel updated", `${panelName.trim()} saved.`);
      } else {
        await createPanel({ name: panelName.trim(), members: roster });
        pushToast("Panel created", `${panelName.trim()} added.`);
      }
      reload();
      setPanelOpen(false);
      setEditingPanel(null);
    } catch (err) {
      pushToast("Save failed", err instanceof Error ? err.message : "Could not save the panel.");
    } finally {
      setSubmitting(false);
    }
  };

  const toggleActive = async (target: ActiveTarget, active: boolean) => {
    setSubmitting(true);
    try {
      if (target.kind === "venue") {
        await setVenueActive(target.entity.id, active);
        pushToast(active ? "Venue activated" : "Venue deactivated", target.entity.name);
      } else {
        await setPanelActive(target.entity.id, active);
        pushToast(active ? "Panel activated" : "Panel deactivated", target.entity.name);
      }
      reload();
      setDeactivating(null);
    } catch (err) {
      pushToast("Update failed", err instanceof Error ? err.message : "Could not update status.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader
        eyebrow="Operations"
        title="Venues & Panels"
        subtitle="Interview venues and evaluation panels available for scheduling across the campus."
        actions={
          <div className="flex gap-2">
            <Button variant="outline" onClick={openCreateVenue}>
              <Plus className="h-4 w-4" /> Venue
            </Button>
            <Button variant="glow" onClick={openCreatePanel}>
              <Plus className="h-4 w-4" /> Panel
            </Button>
          </div>
        }
      />

      <AsyncBoundary loading={loading} error={error} onRetry={reload}>
        <div className="space-y-6">
          {/* Venues */}
          <section>
            <div className="mb-3 flex items-center gap-2">
              <MapPin className="h-4 w-4 text-violet-bright" />
              <CardTitle>Venues ({data.venues.length})</CardTitle>
            </div>
            {data.venues.length === 0 ? (
              <Card className="p-8 text-center text-sm text-muted-foreground">
                No venues yet. Add one to make it selectable when scheduling interviews.
              </Card>
            ) : (
              <div className="space-y-3">
                {data.venues.map((v) => (
                  <DepthCard key={v.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
                    <div>
                      <p className="text-sm font-medium">{v.name}</p>
                      <p className="text-xs text-muted-foreground">
                        {v.capacity != null ? `Capacity ${v.capacity}` : "Capacity not set"}
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      <Badge variant={v.active ? "success" : "muted"}>
                        {v.active ? "Active" : "Inactive"}
                      </Badge>
                      <Button variant="outline" size="sm" onClick={() => openEditVenue(v)}>
                        Edit
                      </Button>
                      {v.active ? (
                        <Button
                          variant="outline"
                          size="sm"
                          className="text-risk"
                          onClick={() => setDeactivating({ kind: "venue", entity: v })}
                        >
                          Deactivate
                        </Button>
                      ) : (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => toggleActive({ kind: "venue", entity: v }, true)}
                          disabled={submitting}
                        >
                          Activate
                        </Button>
                      )}
                    </div>
                  </DepthCard>
                ))}
              </div>
            )}
          </section>

          {/* Panels */}
          <section>
            <div className="mb-3 flex items-center gap-2">
              <Users className="h-4 w-4 text-violet-bright" />
              <CardTitle>Panels ({data.panels.length})</CardTitle>
            </div>
            {data.panels.length === 0 ? (
              <Card className="p-8 text-center text-sm text-muted-foreground">
                No evaluation panels yet. Add one and assign members to use it when scheduling.
              </Card>
            ) : (
              <div className="space-y-3">
                {data.panels.map((p) => (
                  <DepthCard key={p.id} className="p-4">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div>
                        <p className="text-sm font-medium">{p.name}</p>
                        <p className="text-xs text-muted-foreground">
                          {p.members.length} member{p.members.length === 1 ? "" : "s"}
                        </p>
                      </div>
                      <div className="flex items-center gap-2">
                        <Badge variant={p.active ? "success" : "muted"}>
                          {p.active ? "Active" : "Inactive"}
                        </Badge>
                        <Button variant="outline" size="sm" onClick={() => openEditPanel(p)}>
                          Edit
                        </Button>
                        {p.active ? (
                          <Button
                            variant="outline"
                            size="sm"
                            className="text-risk"
                            onClick={() => setDeactivating({ kind: "panel", entity: p })}
                          >
                            Deactivate
                          </Button>
                        ) : (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => toggleActive({ kind: "panel", entity: p }, true)}
                            disabled={submitting}
                          >
                            Activate
                          </Button>
                        )}
                      </div>
                    </div>
                    {p.members.length > 0 && (
                      <div className="mt-3 flex flex-wrap gap-1.5">
                        {p.members.map((m) => (
                          <Badge key={m.id} variant="muted">
                            {m.name}
                          </Badge>
                        ))}
                      </div>
                    )}
                  </DepthCard>
                ))}
              </div>
            )}
          </section>
        </div>
      </AsyncBoundary>

      {/* Venue create / edit */}
      <Dialog
        open={venueOpen}
        onOpenChange={(open) => {
          if (!open) {
            setVenueOpen(false);
            setEditingVenue(null);
          }
        }}
      >
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{editingVenue ? "Edit Venue" : "New Venue"}</DialogTitle>
            <DialogDescription>
              {editingVenue
                ? "Update the venue's name or capacity."
                : "Add an interview venue for this campus."}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="venue-name">
                Name <span className="text-risk">*</span>
              </Label>
              <Input
                id="venue-name"
                value={venueName}
                onChange={(e) => setVenueName(e.target.value)}
                placeholder="e.g. Seminar Hall A"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="venue-cap">Capacity</Label>
              <Input
                id="venue-cap"
                type="number"
                min="1"
                value={venueCapacity}
                onChange={(e) => setVenueCapacity(e.target.value)}
                placeholder="e.g. 30"
              />
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setVenueOpen(false);
                setEditingVenue(null);
              }}
              disabled={submitting}
            >
              Cancel
            </Button>
            <Button variant="glow" onClick={submitVenue} disabled={submitting}>
              {submitting ? "Saving…" : editingVenue ? "Save Changes" : "Create Venue"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Panel create / edit */}
      <Dialog
        open={panelOpen}
        onOpenChange={(open) => {
          if (!open) {
            setPanelOpen(false);
            setEditingPanel(null);
          }
        }}
      >
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{editingPanel ? "Edit Panel" : "New Panel"}</DialogTitle>
            <DialogDescription>
              {editingPanel
                ? "Update the panel and its member roster."
                : "Create an evaluation panel and add its members."}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="panel-name">
                Name <span className="text-risk">*</span>
              </Label>
              <Input
                id="panel-name"
                value={panelName}
                onChange={(e) => setPanelName(e.target.value)}
                placeholder="e.g. Technical Panel 1"
              />
            </div>
            <div className="space-y-2">
              <Label>Members</Label>
              {members.map((m, i) => (
                <div key={i} className="flex items-center gap-2">
                  <Input
                    className="min-w-0"
                    value={m.name}
                    onChange={(e) =>
                      setMembers((prev) =>
                        prev.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)),
                      )
                    }
                    placeholder="Name"
                    aria-label={`Member ${i + 1} name`}
                  />
                  <Input
                    className="min-w-0"
                    value={m.email ?? ""}
                    onChange={(e) =>
                      setMembers((prev) =>
                        prev.map((x, j) => (j === i ? { ...x, email: e.target.value } : x)),
                      )
                    }
                    placeholder="Email (optional)"
                    aria-label={`Member ${i + 1} email`}
                  />
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => setMembers((prev) => prev.filter((_, j) => j !== i))}
                    aria-label={`Remove member ${i + 1}`}
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </div>
              ))}
              <Button
                variant="outline"
                size="sm"
                onClick={() => setMembers((prev) => [...prev, { name: "", email: "" }])}
              >
                <Plus className="h-3.5 w-3.5" /> Add member
              </Button>
              <p className="text-xs text-muted-foreground">
                Rows without a name are ignored. Saving replaces the panel&apos;s roster.
              </p>
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setPanelOpen(false);
                setEditingPanel(null);
              }}
              disabled={submitting}
            >
              Cancel
            </Button>
            <Button variant="glow" onClick={submitPanel} disabled={submitting}>
              {submitting ? "Saving…" : editingPanel ? "Save Changes" : "Create Panel"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Deactivate confirmation */}
      <Dialog open={!!deactivating} onOpenChange={(open) => !open && setDeactivating(null)}>
        <DialogContent className="max-w-md">
          {deactivating && (
            <>
              <DialogHeader>
                <DialogTitle>Deactivate {deactivating.kind}</DialogTitle>
                <DialogDescription>
                  Deactivate{" "}
                  <span className="font-medium text-foreground">{deactivating.entity.name}</span>?
                  It stays in history but can no longer be chosen for new interview slots. You can
                  reactivate it later.
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button variant="outline" onClick={() => setDeactivating(null)} disabled={submitting}>
                  Cancel
                </Button>
                <Button
                  variant="outline"
                  className="text-risk"
                  onClick={() => toggleActive(deactivating, false)}
                  disabled={submitting}
                >
                  {submitting ? "Deactivating…" : "Confirm Deactivate"}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
