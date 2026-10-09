"use client";

import { useEffect, useState } from "react";
import { Sparkles, Check, Trash2, Plus, AlertCircle } from "lucide-react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { useAppStore } from "@/hooks/useAppStore";
import {
  getDriveSkillRequirements,
  createDriveSkillRequirement,
  updateDriveSkillRequirement,
  deleteDriveSkillRequirement,
  approveDriveSkillRequirement,
  approveAllDriveSkillRequirements,
  extractDriveSkillsWithAi,
  type DriveSkillRequirement,
  type UpsertDriveSkillRequirementRequest,
} from "@/lib/api/driveRequirements";
import type { Drive } from "@/types";

interface Props {
  drive: Drive | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function DriveSkillRequirementsDialog({ drive, open, onOpenChange }: Props) {
  const pushToast = useAppStore((s) => s.pushToast);
  const [loading, setLoading] = useState(false);
  const [extracting, setExtracting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [requirements, setRequirements] = useState<DriveSkillRequirement[]>([]);

  // New skill inline form state
  const [newSkillName, setNewSkillName] = useState("");
  const [newSkillLevel, setNewSkillLevel] = useState(70);
  const [newSkillWeight, setNewSkillWeight] = useState(2);
  const [newSkillMandatory, setNewSkillMandatory] = useState(true);

  const loadRequirements = async (driveId: string) => {
    setLoading(true);
    try {
      const data = await getDriveSkillRequirements(driveId, false);
      setRequirements(data);
    } catch (err) {
      pushToast("Failed to load skills", err instanceof Error ? err.message : "Error loading requirements");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open && drive) {
      loadRequirements(drive.id);
    } else {
      setRequirements([]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, drive]);

  if (!drive) return null;

  const handleExtractAi = async () => {
    setExtracting(true);
    try {
      const res = await extractDriveSkillsWithAi(drive.id);
      pushToast("AI Extraction Complete", `Extracted ${res.extractedSkills.length} skills from Job Description.`);
      await loadRequirements(drive.id);
    } catch (err) {
      pushToast("AI Extraction Failed", err instanceof Error ? err.message : "Could not extract skills");
    } finally {
      setExtracting(false);
    }
  };

  const handleAddSkill = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newSkillName.trim()) return;

    try {
      const req: UpsertDriveSkillRequirementRequest = {
        skill: newSkillName.trim(),
        requiredLevel: newSkillLevel,
        weight: newSkillWeight,
        mandatory: newSkillMandatory,
        approved: true,
        source: "OFFICER",
      };
      await createDriveSkillRequirement(drive.id, req);
      setNewSkillName("");
      setNewSkillLevel(70);
      setNewSkillWeight(2);
      pushToast("Skill Added", `Added ${req.skill} (${req.requiredLevel}%) to requirements.`);
      await loadRequirements(drive.id);
    } catch (err) {
      pushToast("Failed to add skill", err instanceof Error ? err.message : "Error adding requirement");
    }
  };

  const handleLevelChange = async (req: DriveSkillRequirement, newLevel: number) => {
    try {
      await updateDriveSkillRequirement(drive.id, req.id, {
        skill: req.skill,
        requiredLevel: newLevel,
        weight: req.weight,
        mandatory: req.mandatory,
        approved: req.approved,
        source: req.source,
      });
      setRequirements((prev) =>
        prev.map((r) => (r.id === req.id ? { ...r, requiredLevel: newLevel } : r)),
      );
    } catch (err) {
      pushToast("Update failed", err instanceof Error ? err.message : "Error updating level");
    }
  };

  const handleToggleMandatory = async (req: DriveSkillRequirement) => {
    try {
      const updated = !req.mandatory;
      await updateDriveSkillRequirement(drive.id, req.id, {
        skill: req.skill,
        requiredLevel: req.requiredLevel,
        weight: req.weight,
        mandatory: updated,
        approved: req.approved,
        source: req.source,
      });
      setRequirements((prev) =>
        prev.map((r) => (r.id === req.id ? { ...r, mandatory: updated } : r)),
      );
    } catch (err) {
      pushToast("Update failed", err instanceof Error ? err.message : "Error updating requirement");
    }
  };

  const handleDelete = async (reqId: string, skill: string) => {
    try {
      await deleteDriveSkillRequirement(drive.id, reqId);
      setRequirements((prev) => prev.filter((r) => r.id !== reqId));
      pushToast("Skill Removed", `Removed ${skill} from requirements.`);
    } catch (err) {
      pushToast("Delete failed", err instanceof Error ? err.message : "Error removing skill");
    }
  };

  const handleApproveSingle = async (reqId: string, skill: string) => {
    try {
      await approveDriveSkillRequirement(drive.id, reqId);
      setRequirements((prev) =>
        prev.map((r) => (r.id === reqId ? { ...r, approved: true } : r)),
      );
      pushToast("Skill Approved", `Approved requirement: ${skill}`);
    } catch (err) {
      pushToast("Approval failed", err instanceof Error ? err.message : "Error approving requirement");
    }
  };

  const handleApproveAll = async () => {
    setSaving(true);
    try {
      const count = await approveAllDriveSkillRequirements(drive.id);
      pushToast("Requirements Approved", `Approved ${count} skill requirements for ${drive.companyName}.`);
      await loadRequirements(drive.id);
    } catch (err) {
      pushToast("Approval failed", err instanceof Error ? err.message : "Error approving requirements");
    } finally {
      setSaving(false);
    }
  };

  const approvedCount = requirements.filter((r) => r.approved).length;
  const pendingCount = requirements.length - approvedCount;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <div className="flex items-center justify-between gap-2 pr-6">
            <div>
              <DialogTitle className="flex items-center gap-2">
                <span>Skill Requirements & Level Targets</span>
                <Badge variant="outline" className="font-mono text-xs">
                  {drive.companyName} · {drive.role}
                </Badge>
              </DialogTitle>
              <DialogDescription className="mt-1">
                Derived from the Job Description and reviewed by Placement Officers. Students will be evaluated specifically against these approved levels.
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        {/* AI Auto-Extraction Banner */}
        <div className="flex flex-col gap-3 rounded-lg border border-violet/30 bg-violet/5 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="flex items-center gap-1.5 font-medium text-sm text-foreground">
              <Sparkles className="h-4 w-4 text-violet-bright" />
              <span>Spring AI JD Skill Extraction</span>
            </div>
            <p className="mt-0.5 text-xs text-muted-foreground">
              Automatically extract required technical skills, target proficiency levels, and weights from the JD.
            </p>
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleExtractAi}
            disabled={extracting}
            className="shrink-0 gap-1.5"
          >
            <Sparkles className="h-3.5 w-3.5 text-violet-bright" />
            {extracting ? "Extracting with AI…" : "Auto-extract with AI"}
          </Button>
        </div>

        {/* Existing requirements table */}
        <div className="max-h-[45vh] space-y-2 overflow-y-auto pr-1">
          {loading ? (
            <div className="py-8 text-center text-sm text-muted-foreground">
              Loading drive skill requirements…
            </div>
          ) : requirements.length === 0 ? (
            <div className="rounded-lg border border-dashed border-border py-8 text-center">
              <AlertCircle className="mx-auto mb-2 h-6 w-6 text-muted-foreground" />
              <p className="text-sm font-medium">No skill requirements defined yet</p>
              <p className="text-xs text-muted-foreground mt-1">
                Click &quot;Auto-extract with AI&quot; or manually add required technical skills below.
              </p>
            </div>
          ) : (
            <div className="space-y-2">
              <div className="flex items-center justify-between px-1 text-xs text-muted-foreground">
                <span>{requirements.length} Requirements ({approvedCount} Approved, {pendingCount} Pending Review)</span>
                {pendingCount > 0 && (
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={handleApproveAll}
                    disabled={saving}
                    className="h-7 text-xs text-success hover:text-success hover:bg-success/10"
                  >
                    <Check className="mr-1 h-3.5 w-3.5" /> Approve All Pending
                  </Button>
                )}
              </div>

              {requirements.map((req) => (
                <div
                  key={req.id}
                  className={`flex items-center justify-between gap-3 rounded-lg border p-3 text-sm transition-colors ${
                    req.approved ? "border-border bg-card" : "border-warning/40 bg-warning/5"
                  }`}
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-foreground">{req.skill}</span>
                      <button
                        type="button"
                        onClick={() => handleToggleMandatory(req)}
                        className={`rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wider transition-colors ${
                          req.mandatory
                            ? "bg-risk/15 text-risk"
                            : "bg-muted text-muted-foreground"
                        }`}
                      >
                        {req.mandatory ? "Mandatory" : "Preferred"}
                      </button>
                      {req.source === "AI_EXTRACTED" && (
                        <Badge variant="outline" className="text-[10px] text-violet-bright">
                          AI {req.confidence ? `${req.confidence}%` : ""}
                        </Badge>
                      )}
                      {!req.approved ? (
                        <Badge variant="outline" className="border-warning text-warning text-[10px]">
                          Pending Approval
                        </Badge>
                      ) : (
                        <Badge variant="outline" className="border-success/40 bg-success/10 text-success text-[10px]">
                          Approved ✓
                        </Badge>
                      )}
                    </div>

                    <div className="mt-2 flex items-center gap-4 text-xs">
                      <div className="flex items-center gap-2">
                        <span className="text-muted-foreground">Required Floor:</span>
                        <input
                          type="range"
                          min={20}
                          max={100}
                          step={5}
                          value={req.requiredLevel}
                          onChange={(e) => handleLevelChange(req, Number(e.target.value))}
                          className="h-1.5 w-24 cursor-pointer accent-violet"
                        />
                        <span className="font-mono font-medium text-foreground">{req.requiredLevel}%</span>
                      </div>
                      <div className="text-muted-foreground">
                        Weight: <span className="font-medium text-foreground">{req.weight}x</span>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-1.5">
                    {!req.approved && (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => handleApproveSingle(req.id, req.skill)}
                        className="h-7 text-xs text-success border-success/40 hover:bg-success/10 gap-1 font-medium px-2"
                      >
                        <Check className="h-3.5 w-3.5" /> Approve
                      </Button>
                    )}
                    <Button
                      size="icon"
                      variant="ghost"
                      onClick={() => handleDelete(req.id, req.skill)}
                      className="h-8 w-8 text-muted-foreground hover:text-risk"
                      title="Remove requirement"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Add Skill Manual Form */}
        <form onSubmit={handleAddSkill} className="rounded-lg border border-border bg-muted/40 p-3">
          <Label className="text-xs text-muted-foreground">Add Custom Skill Requirement</Label>
          <div className="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-[1.5fr_1fr_1fr_auto_auto]">
            <Input
              placeholder="Skill name (e.g. Docker, Kafka)"
              value={newSkillName}
              onChange={(e) => setNewSkillName(e.target.value)}
              className="h-8 text-xs"
            />
            <div className="flex items-center gap-1.5 px-1">
              <span className="text-[11px] text-muted-foreground whitespace-nowrap">Level:</span>
              <Input
                type="number"
                min={10}
                max={100}
                value={newSkillLevel}
                onChange={(e) => setNewSkillLevel(Number(e.target.value))}
                className="h-8 text-xs"
              />
            </div>
            <div className="flex items-center gap-1.5 px-1">
              <span className="text-[11px] text-muted-foreground whitespace-nowrap">Weight:</span>
              <Input
                type="number"
                min={1}
                max={5}
                value={newSkillWeight}
                onChange={(e) => setNewSkillWeight(Number(e.target.value))}
                className="h-8 text-xs"
              />
            </div>
            <Button
              type="button"
              variant={newSkillMandatory ? "default" : "outline"}
              size="sm"
              onClick={() => setNewSkillMandatory(!newSkillMandatory)}
              className="h-8 text-xs whitespace-nowrap"
            >
              {newSkillMandatory ? "Mandatory" : "Preferred"}
            </Button>
            <Button type="submit" size="sm" variant="glow" className="h-8 text-xs gap-1">
              <Plus className="h-3.5 w-3.5" /> Add
            </Button>
          </div>
        </form>

        <DialogFooter className="mt-2">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Close
          </Button>
          {pendingCount > 0 && (
            <Button variant="glow" onClick={handleApproveAll} disabled={saving}>
              Approve All & Publish ({pendingCount})
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
