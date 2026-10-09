"use client";

import { useState, useMemo } from "react";
import {
  Award,
  Sparkles,
  IndianRupee,
  CheckCircle2,
  Clock,
  FileCheck2,
  LogIn,
  Plus,
  Search,
  RotateCcw,
  AlertTriangle,
  X,
  Eye,
  BellRing,
  MapPin,
  Calendar,
  TrendingUp,
} from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { DepthCard } from "@/components/intelligence/DepthCard";
import {
  listOffers,
  extendOffer,
  revokeOffer,
  verifyOfferDocuments,
  markOfferJoined,
  nudgeOffer,
} from "@/lib/api/offers";
import { listStudentsByCampus } from "@/lib/api/students";
import { listDrivesByCampus } from "@/lib/api/drives";
import { getAnalyticsForCampus } from "@/lib/api/analytics";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { offerStatusTone } from "@/lib/status";
import type { Offer, Student, Drive, OfferType } from "@/types";

interface OfferWithStudent extends Offer {
  student?: Student;
}

const OFFER_TYPES: OfferType[] = ["Full-Time", "Internship", "PPO", "Internship + PPO"];

function parsePackageLpa(pkg: string): number {
  if (!pkg) return 0;
  const match = pkg.match(/([\d.]+)/);
  return match ? parseFloat(match[1]) : 0;
}

export default function OfficerOffersPage() {
  const campusId = useAppStore((s) => s.activeCampusId);
  const pushToast = useAppStore((s) => s.pushToast);

  // Operation / Busy state
  const [busyId, setBusyId] = useState<string | null>(null);

  // Filters
  const [searchQuery, setSearchQuery] = useState("");
  const [companyFilter, setCompanyFilter] = useState("all");
  const [typeFilter, setTypeFilter] = useState("all");
  const [statusTab, setStatusTab] = useState<"ALL" | "PENDING" | "ACCEPTED" | "JOINED" | "PPO" | "OTHER">("ALL");
  const [sortBy, setSortBy] = useState<"DEFAULT" | "PACKAGE_HIGH" | "DEADLINE">("DEFAULT");

  // Dialog States
  const [creating, setCreating] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [viewingOffer, setViewingOffer] = useState<OfferWithStudent | null>(null);
  const [revoking, setRevoking] = useState<OfferWithStudent | null>(null);

  // Create Offer Form Fields
  const [fStudentId, setFStudentId] = useState("");
  const [fDriveId, setFDriveId] = useState("");
  const [fRole, setFRole] = useState("");
  const [fPackageLpa, setFPackageLpa] = useState("");
  const [fOfferType, setFOfferType] = useState<OfferType>("Full-Time");
  const [fLocation, setFLocation] = useState("");
  const [fJoiningDate, setFJoiningDate] = useState("");
  const [fDeadline, setFDeadline] = useState("");
  const [fFromInternship, setFFromInternship] = useState("");

  // Live data fetch
  const { data, loading, error, reload } = useApiResource(
    async () => {
      const [offers, students, drives, analytics] = await Promise.all([
        listOffers(campusId),
        listStudentsByCampus(campusId),
        listDrivesByCampus(campusId),
        getAnalyticsForCampus(campusId).catch(() => null),
      ]);
      const byId = new Map(students.map((s) => [s.id, s]));

      // Do NOT filter out unlinked students; preserve every offer with a safe fallback
      const offerRows: OfferWithStudent[] = offers.map((o) => ({
        ...o,
        student: byId.get(o.studentId) ?? ({
          id: o.studentId,
          name: `Candidate (${o.studentId.slice(0, 8)})`,
          rollNumber: "—",
          avatarInitials: "CD",
          department: "",
        } as unknown as Student),
      }));

      return {
        offers: offerRows,
        students,
        drives,
        offersCount: analytics?.snapshot.offersCount ?? null,
      };
    },
    [campusId],
    { offers: [] as OfferWithStudent[], students: [] as Student[], drives: [] as Drive[], offersCount: null as number | null },
  );

  const { offers, students, drives } = data;
  const offersCount = data.offersCount ?? offers.length;

  const companies = Array.from(new Set(offers.map((o) => o.companyName).filter(Boolean)));

  // Analytics Metrics
  const acceptedOffers = offers.filter((o) => o.status === "ACCEPTED");
  const joinedOffers = offers.filter((o) => o.joined);
  const ppoOffers = offers.filter((o) => o.offerType.includes("PPO"));
  const pendingOffers = offers.filter(
    (o) => o.status === "OFFER RECEIVED" || o.status === "PENDING ACCEPTANCE",
  );

  const acceptanceRate = offers.length ? Math.round((acceptedOffers.length / offers.length) * 100) : 0;

  const { highestLpa, averageLpa } = useMemo(() => {
    const packages = offers.map((o) => parsePackageLpa(o.package)).filter((p) => p > 0);
    if (!packages.length) return { highestLpa: 0, averageLpa: 0 };
    const max = Math.max(...packages);
    const avg = packages.reduce((a, b) => a + b, 0) / packages.length;
    return { highestLpa: max, averageLpa: Math.round(avg * 10) / 10 };
  }, [offers]);

  // Filtered & Sorted Rows
  const filteredRows = useMemo(() => {
    let result = offers.filter((o) => {
      // 1. Status Tab filter
      if (statusTab === "PENDING" && o.status !== "OFFER RECEIVED" && o.status !== "PENDING ACCEPTANCE")
        return false;
      if (statusTab === "ACCEPTED" && o.status !== "ACCEPTED") return false;
      if (statusTab === "JOINED" && !o.joined) return false;
      if (statusTab === "PPO" && !o.offerType.includes("PPO")) return false;
      if (statusTab === "OTHER" && o.status !== "DECLINED") return false;

      // 2. Company filter
      if (companyFilter !== "all" && o.companyName !== companyFilter) return false;

      // 3. Offer type filter
      if (typeFilter !== "all" && o.offerType !== typeFilter) return false;

      // 4. Search Query
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const candidateName = o.student?.name?.toLowerCase() ?? "";
        const rollNumber = o.student?.rollNumber?.toLowerCase() ?? "";
        const companyName = o.companyName?.toLowerCase() ?? "";
        const role = o.role?.toLowerCase() ?? "";
        const location = o.location?.toLowerCase() ?? "";

        const match =
          candidateName.includes(q) ||
          rollNumber.includes(q) ||
          companyName.includes(q) ||
          role.includes(q) ||
          location.includes(q);

        if (!match) return false;
      }

      return true;
    });

    // Sorting
    if (sortBy === "PACKAGE_HIGH") {
      result = [...result].sort((a, b) => parsePackageLpa(b.package) - parsePackageLpa(a.package));
    } else if (sortBy === "DEADLINE") {
      result = [...result].sort((a, b) => (a.acceptanceDeadline || "").localeCompare(b.acceptanceDeadline || ""));
    }

    return result;
  }, [offers, statusTab, companyFilter, typeFilter, searchQuery, sortBy]);

  const hasActiveFilters =
    searchQuery.trim() !== "" || companyFilter !== "all" || typeFilter !== "all" || statusTab !== "ALL" || sortBy !== "DEFAULT";

  const clearFilters = () => {
    setSearchQuery("");
    setCompanyFilter("all");
    setTypeFilter("all");
    setStatusTab("ALL");
    setSortBy("DEFAULT");
  };

  // Helper for asynchronous action dispatch
  const runAction = async (
    offerId: string,
    action: (id: string) => Promise<unknown>,
    okTitle: string,
    okBody: string,
  ) => {
    setBusyId(offerId);
    try {
      await action(offerId);
      pushToast(okTitle, okBody);
      reload();
    } catch (err) {
      pushToast(
        "Action failed",
        err instanceof Error ? err.message : "Could not complete the action. Please try again.",
      );
    } finally {
      setBusyId(null);
    }
  };

  // Open Create Offer modal
  const openCreateOffer = () => {
    setFStudentId(students[0]?.id ?? "");
    const firstDrive = drives[0];
    setFDriveId(firstDrive?.id ?? "");
    setFRole(firstDrive?.role ?? "");
    const pkg = firstDrive?.package ? parsePackageLpa(firstDrive.package) : "";
    setFPackageLpa(pkg ? String(pkg) : "");
    setFOfferType("Full-Time");
    setFLocation(firstDrive?.location ?? "");
    setFJoiningDate("");
    setFDeadline("");
    setFFromInternship("");
    setCreating(true);
  };

  const handleDriveChange = (driveId: string) => {
    setFDriveId(driveId);
    const drive = drives.find((d) => d.id === driveId);
    if (drive) {
      if (drive.role) setFRole(drive.role);
      if (drive.location) setFLocation(drive.location);
      const pkg = parsePackageLpa(drive.package);
      if (pkg) setFPackageLpa(String(pkg));
    }
  };

  const submitCreateOffer = async () => {
    const pkgNum = parseFloat(fPackageLpa);
    if (!fStudentId || !fDriveId || !fRole.trim() || Number.isNaN(pkgNum) || pkgNum <= 0) {
      pushToast("Missing details", "Candidate, drive, role, and valid package (LPA > 0) are required.");
      return;
    }
    const drive = drives.find((d) => d.id === fDriveId);
    setSubmitting(true);
    try {
      await extendOffer({
        studentId: fStudentId,
        driveId: fDriveId,
        recruiterId: drive?.companyId || "recruiter_officer",
        role: fRole.trim(),
        packageLpa: pkgNum,
        companyName: drive?.companyName ?? null,
        offerType: fOfferType,
        location: fLocation.trim() || null,
        joiningDate: fJoiningDate || null,
        acceptanceDeadline: fDeadline || null,
        fromInternship: fOfferType.includes("PPO") ? (fFromInternship.trim() || drive?.companyName || null) : null,
      });
      pushToast(
        "Offer recorded",
        `New offer of ₹${pkgNum} LPA extended to ${students.find((s) => s.id === fStudentId)?.name ?? "candidate"}.`,
      );
      reload();
      setCreating(false);
    } catch (err) {
      pushToast("Failed to extend offer", err instanceof Error ? err.message : "Request could not be completed.");
    } finally {
      setSubmitting(false);
    }
  };

  const confirmRevoke = async () => {
    if (!revoking) return;
    setSubmitting(true);
    try {
      await revokeOffer(revoking.id);
      pushToast("Offer revoked", `Offer from ${revoking.companyName} was revoked.`);
      reload();
      setRevoking(null);
    } catch (err) {
      pushToast("Revocation failed", err instanceof Error ? err.message : "Could not revoke the offer.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-6xl space-y-6 pb-12">
      <PageHeader
        eyebrow="Operations"
        title="Offers"
        subtitle="Track job offers, PPO conversions, document verification, and joining milestones across placement cycles."
        actions={
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => reload()} disabled={loading}>
              <RotateCcw className={`h-3.5 w-3.5 mr-1.5 ${loading ? "animate-spin" : ""}`} /> Refresh
            </Button>
            <Button variant="glow" onClick={openCreateOffer} disabled={drives.length === 0}>
              <Plus className="h-4 w-4 mr-1.5" /> Extend Offer
            </Button>
          </div>
        }
      />

      {/* KPI Stat Cards */}
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-5">
        <StatCard
          label="Total Offers"
          value={offersCount}
          icon={Award}
          accent="violet"
          trend={`${acceptanceRate}% accepted`}
          trendPositive={acceptanceRate >= 50}
        />
        <StatCard
          label="Accepted Offers"
          value={acceptedOffers.length}
          icon={CheckCircle2}
          accent="success"
          trend={`${joinedOffers.length} confirmed joined`}
          trendPositive={true}
        />
        <StatCard
          label="Awaiting Decision"
          value={pendingOffers.length}
          icon={Clock}
          accent="warning"
          trend={pendingOffers.length > 0 ? "Requires nudge" : "All decided"}
          trendPositive={pendingOffers.length === 0}
        />
        <StatCard
          label="Highest CTC"
          value={highestLpa > 0 ? `₹${highestLpa}L` : "—"}
          icon={TrendingUp}
          accent="success"
        />
        <StatCard
          label="Average CTC"
          value={averageLpa > 0 ? `₹${averageLpa}L` : "—"}
          icon={IndianRupee}
          accent="violet"
          trend={`${ppoOffers.length} PPOs logged`}
          trendPositive={true}
        />
      </div>

      {/* PPO Tracking Card */}
      {ppoOffers.length > 0 && (
        <DepthCard className="p-4 border-warning/30 bg-warning/5">
          <div className="mb-3 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Sparkles className="h-4 w-4 text-warning" />
              <CardTitle className="text-sm font-semibold">Pre-Placement Offers (PPOs)</CardTitle>
            </div>
            <Badge variant="warning" className="text-xs">
              {ppoOffers.length} PPO{ppoOffers.length === 1 ? "" : "s"}
            </Badge>
          </div>
          <div className="grid gap-2.5 sm:grid-cols-2 lg:grid-cols-3">
            {ppoOffers.slice(0, 6).map((o) => (
              <div
                key={o.id}
                onClick={() => setViewingOffer(o)}
                className="cursor-pointer rounded-lg border border-warning/20 bg-card/60 p-2.5 transition hover:border-warning/50 hover:bg-card"
              >
                <div className="flex items-center justify-between gap-2">
                  <p className="text-xs font-semibold text-foreground truncate">{o.student?.name}</p>
                  <Badge variant={offerStatusTone[o.status]} className="text-[10px]">
                    {o.status}
                  </Badge>
                </div>
                <p className="text-[11px] text-muted-foreground mt-0.5 truncate">
                  {o.companyName} · {o.role}
                </p>
                <div className="mt-2 flex items-center justify-between text-[11px]">
                  <span className="font-semibold text-success tabular-nums">{o.package}</span>
                  <span className="text-muted-foreground text-[10px]">Due: {o.acceptanceDeadline || "—"}</span>
                </div>
              </div>
            ))}
          </div>
        </DepthCard>
      )}

      {/* Filter and Search Bar */}
      <div className="space-y-3">
        {/* Status Tab Pills */}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-1.5 rounded-lg border border-border bg-card p-1">
            <button
              type="button"
              onClick={() => setStatusTab("ALL")}
              className={`rounded-md px-3 py-1.5 text-xs font-medium transition ${
                statusTab === "ALL"
                  ? "bg-violet-bright/15 text-violet-bright font-semibold shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              All ({offers.length})
            </button>
            <button
              type="button"
              onClick={() => setStatusTab("PENDING")}
              className={`rounded-md px-3 py-1.5 text-xs font-medium transition ${
                statusTab === "PENDING"
                  ? "bg-violet-bright/15 text-violet-bright font-semibold shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              Pending ({pendingOffers.length})
            </button>
            <button
              type="button"
              onClick={() => setStatusTab("ACCEPTED")}
              className={`rounded-md px-3 py-1.5 text-xs font-medium transition ${
                statusTab === "ACCEPTED"
                  ? "bg-violet-bright/15 text-violet-bright font-semibold shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              Accepted ({acceptedOffers.length})
            </button>
            <button
              type="button"
              onClick={() => setStatusTab("JOINED")}
              className={`rounded-md px-3 py-1.5 text-xs font-medium transition ${
                statusTab === "JOINED"
                  ? "bg-violet-bright/15 text-violet-bright font-semibold shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              Joined ({joinedOffers.length})
            </button>
            <button
              type="button"
              onClick={() => setStatusTab("PPO")}
              className={`rounded-md px-3 py-1.5 text-xs font-medium transition ${
                statusTab === "PPO"
                  ? "bg-violet-bright/15 text-violet-bright font-semibold shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              PPO ({ppoOffers.length})
            </button>
          </div>

          {hasActiveFilters && (
            <Button
              variant="ghost"
              size="sm"
              onClick={clearFilters}
              className="text-xs text-muted-foreground hover:text-foreground"
            >
              <RotateCcw className="h-3.5 w-3.5 mr-1" /> Reset Filters
            </Button>
          )}
        </div>

        {/* Filter Controls Row */}
        <div className="grid gap-2.5 sm:grid-cols-2 md:grid-cols-4">
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search student, roll no, company, role..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-8 text-xs h-9"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery("")}
                className="absolute right-2.5 top-2.5 text-muted-foreground hover:text-foreground"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            )}
          </div>

          <Select value={companyFilter} onValueChange={setCompanyFilter}>
            <SelectTrigger className="h-9 text-xs" aria-label="Filter by company">
              <SelectValue placeholder="All Companies" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Companies</SelectItem>
              {companies.map((c) => (
                <SelectItem key={c} value={c}>
                  {c}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select value={typeFilter} onValueChange={setTypeFilter}>
            <SelectTrigger className="h-9 text-xs" aria-label="Filter by offer type">
              <SelectValue placeholder="All Offer Types" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Offer Types</SelectItem>
              {OFFER_TYPES.map((t) => (
                <SelectItem key={t} value={t}>
                  {t}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select value={sortBy} onValueChange={(v) => setSortBy(v as typeof sortBy)}>
            <SelectTrigger className="h-9 text-xs" aria-label="Sort offers">
              <SelectValue placeholder="Sort Offers" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="DEFAULT">Default Order</SelectItem>
              <SelectItem value="PACKAGE_HIGH">Highest Package (CTC)</SelectItem>
              <SelectItem value="DEADLINE">Nearest Deadline</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {/* Offers Table */}
        <Card className="overflow-x-auto p-0 border border-border">
          <Table>
            <TableHeader>
              <TableRow className="bg-muted/40 hover:bg-muted/40">
                <TableHead className="w-[180px]">Student</TableHead>
                <TableHead>Company</TableHead>
                <TableHead>Role</TableHead>
                <TableHead>CTC (Package)</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Offered</TableHead>
                <TableHead>Accept By</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Docs</TableHead>
                <TableHead>Joined</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                Array.from({ length: 5 }).map((_, idx) => (
                  <TableRow key={idx} className="animate-pulse">
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <div className="h-7 w-7 rounded-full bg-muted" />
                        <div className="h-4 w-24 rounded bg-muted" />
                      </div>
                    </TableCell>
                    <TableCell><div className="h-4 w-20 rounded bg-muted" /></TableCell>
                    <TableCell><div className="h-4 w-16 rounded bg-muted" /></TableCell>
                    <TableCell><div className="h-4 w-16 rounded bg-muted" /></TableCell>
                    <TableCell><div className="h-4 w-14 rounded bg-muted" /></TableCell>
                    <TableCell><div className="h-4 w-16 rounded bg-muted" /></TableCell>
                    <TableCell><div className="h-4 w-16 rounded bg-muted" /></TableCell>
                    <TableCell><div className="h-5 w-16 rounded-full bg-muted" /></TableCell>
                    <TableCell><div className="h-5 w-14 rounded-full bg-muted" /></TableCell>
                    <TableCell><div className="h-5 w-10 rounded-full bg-muted" /></TableCell>
                    <TableCell className="text-right"><div className="h-7 w-16 rounded bg-muted ml-auto" /></TableCell>
                  </TableRow>
                ))
              ) : error ? (
                <TableRow>
                  <TableCell colSpan={11} className="py-12 text-center">
                    <div className="flex flex-col items-center justify-center gap-2">
                      <AlertTriangle className="h-6 w-6 text-risk" />
                      <p className="text-sm font-medium text-foreground">Failed to load offers</p>
                      <p className="text-xs text-muted-foreground">{error}</p>
                      <Button variant="outline" size="sm" onClick={() => reload()} className="mt-2">
                        Try Again
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ) : filteredRows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={11} className="py-12 text-center">
                    <div className="flex flex-col items-center justify-center gap-2">
                      <Award className="h-8 w-8 text-muted-foreground/60" />
                      <p className="text-sm font-medium text-foreground">
                        {hasActiveFilters ? "No matching offers found" : "No offers recorded for this campus yet"}
                      </p>
                      <p className="text-xs text-muted-foreground">
                        {hasActiveFilters
                          ? "Try clearing or broadening your search filters."
                          : "Extend offers directly or log candidate selections from live drives."}
                      </p>
                      {hasActiveFilters ? (
                        <Button variant="outline" size="sm" onClick={clearFilters} className="mt-2 text-xs">
                          Clear Filters
                        </Button>
                      ) : (
                        <Button variant="glow" size="sm" onClick={openCreateOffer} className="mt-2 text-xs">
                          <Plus className="h-3.5 w-3.5 mr-1" /> Extend Offer
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ) : (
                filteredRows.map((o) => (
                  <TableRow key={o.id} className="transition hover:bg-muted/30">
                    <TableCell>
                      <div className="flex items-center gap-2.5">
                        <Avatar className="h-7 w-7">
                          <AvatarFallback className="text-[10px] font-semibold">
                            {o.student?.avatarInitials ?? "CD"}
                          </AvatarFallback>
                        </Avatar>
                        <div className="min-w-0">
                          <p className="text-xs font-semibold text-foreground truncate">{o.student?.name}</p>
                          <p className="text-[10px] text-muted-foreground">{o.student?.rollNumber || "ID: " + o.studentId.slice(0, 6)}</p>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="text-xs font-medium text-foreground">{o.companyName}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">{o.role}</TableCell>
                    <TableCell className="text-xs font-semibold text-foreground tabular-nums">{o.package}</TableCell>
                    <TableCell>
                      <Badge variant={o.offerType.includes("PPO") ? "warning" : "muted"} className="text-[10px]">
                        {o.offerType}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">{o.offerDate || "—"}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">{o.acceptanceDeadline || "—"}</TableCell>
                    <TableCell>
                      <Badge variant={offerStatusTone[o.status]}>{o.status}</Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={o.documentsVerified ? "success" : "muted"}>
                        {o.documentsVerified ? "Verified" : "Pending"}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={o.joined ? "success" : "muted"}>{o.joined ? "Yes" : "No"}</Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <Button
                          size="sm"
                          variant="ghost"
                          className="h-7 w-7 p-0 text-muted-foreground hover:text-foreground"
                          title="View offer details"
                          onClick={() => setViewingOffer(o)}
                        >
                          <Eye className="h-3.5 w-3.5" />
                        </Button>

                        {/* Officer actions for accepted offers: verify docs -> confirm join */}
                        {o.status === "ACCEPTED" && !o.joined && (
                          !o.documentsVerified ? (
                            <Button
                              size="sm"
                              variant="outline"
                              className="h-7 text-xs"
                              disabled={busyId === o.id}
                              onClick={() =>
                                runAction(
                                  o.id,
                                  verifyOfferDocuments,
                                  "Documents verified",
                                  `${o.student?.name ?? "Student"} moved to document verification.`,
                                )
                              }
                            >
                              <FileCheck2 className="h-3 w-3 mr-1" /> Verify docs
                            </Button>
                          ) : (
                            <Button
                              size="sm"
                              variant="glow"
                              className="h-7 text-xs"
                              disabled={busyId === o.id}
                              onClick={() =>
                                runAction(
                                  o.id,
                                  markOfferJoined,
                                  "Marked joined",
                                  `${o.student?.name ?? "Student"} confirmed as joined.`,
                                )
                              }
                            >
                              <LogIn className="h-3 w-3 mr-1" /> Mark joined
                            </Button>
                          )
                        )}

                        {/* Actions for pending decision offers: Nudge candidate, Escalate, Revoke */}
                        {(o.status === "OFFER RECEIVED" || o.status === "PENDING ACCEPTANCE") && (
                          <>
                            <Button
                              size="sm"
                              variant="outline"
                              className="h-7 text-xs"
                              disabled={busyId === o.id}
                              title="Send reminder to candidate"
                              onClick={() =>
                                runAction(
                                  o.id,
                                  nudgeOffer,
                                  "Reminder sent",
                                  `Nudge notification sent to ${o.student?.name ?? "the candidate"}.`,
                                )
                              }
                            >
                              <BellRing className="h-3 w-3 mr-1" /> Nudge
                            </Button>
                            <Button
                              size="sm"
                              variant="ghost"
                              className="h-7 text-xs text-risk hover:bg-risk/10"
                              title="Revoke offer"
                              onClick={() => setRevoking(o)}
                            >
                              Revoke
                            </Button>
                          </>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </Card>
      </div>

      {/* Extend Offer Dialog */}
      <Dialog open={creating} onOpenChange={(open) => !open && setCreating(false)}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Extend / Record Offer</DialogTitle>
            <DialogDescription>
              Record an official placement offer or pre-placement offer (PPO) for a candidate.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="off-student">
                  Candidate <span className="text-risk">*</span>
                </Label>
                <select
                  id="off-student"
                  value={fStudentId}
                  onChange={(e) => setFStudentId(e.target.value)}
                  className="flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                >
                  <option value="">Select candidate…</option>
                  {students.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name} ({s.rollNumber || s.department || "Student"})
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="off-drive">
                  Recruitment Drive <span className="text-risk">*</span>
                </Label>
                <select
                  id="off-drive"
                  value={fDriveId}
                  onChange={(e) => handleDriveChange(e.target.value)}
                  className="flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                >
                  <option value="">Select drive…</option>
                  {drives.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.companyName} — {d.role}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="off-role">
                  Designation / Role <span className="text-risk">*</span>
                </Label>
                <Input
                  id="off-role"
                  value={fRole}
                  onChange={(e) => setFRole(e.target.value)}
                  placeholder="e.g. Software Engineer"
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="off-pkg">
                  Package (CTC in LPA) <span className="text-risk">*</span>
                </Label>
                <Input
                  id="off-pkg"
                  type="number"
                  step="0.1"
                  min="0.5"
                  value={fPackageLpa}
                  onChange={(e) => setFPackageLpa(e.target.value)}
                  placeholder="e.g. 14.5"
                />
              </div>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="off-type">Offer Type</Label>
                <select
                  id="off-type"
                  value={fOfferType}
                  onChange={(e) => setFOfferType(e.target.value as OfferType)}
                  className="flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                >
                  {OFFER_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="off-loc">Work Location</Label>
                <Input
                  id="off-loc"
                  value={fLocation}
                  onChange={(e) => setFLocation(e.target.value)}
                  placeholder="e.g. Bengaluru / Hybrid"
                />
              </div>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="off-deadline">Acceptance Deadline</Label>
                <Input
                  id="off-deadline"
                  type="date"
                  value={fDeadline}
                  onChange={(e) => setFDeadline(e.target.value)}
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="off-joining">Tentative Joining Date</Label>
                <Input
                  id="off-joining"
                  type="date"
                  value={fJoiningDate}
                  onChange={(e) => setFJoiningDate(e.target.value)}
                />
              </div>
            </div>

            {fOfferType.includes("PPO") && (
              <div className="space-y-1.5">
                <Label htmlFor="off-from-intern">PPO Internship Company (Optional)</Label>
                <Input
                  id="off-from-intern"
                  value={fFromInternship}
                  onChange={(e) => setFFromInternship(e.target.value)}
                  placeholder="Company name where internship was completed"
                />
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreating(false)} disabled={submitting}>
              Cancel
            </Button>
            <Button variant="glow" onClick={submitCreateOffer} disabled={submitting}>
              {submitting ? "Extending…" : "Extend Offer"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Offer Details Dialog */}
      <Dialog open={!!viewingOffer} onOpenChange={(open) => !open && setViewingOffer(null)}>
        <DialogContent className="max-w-md">
          {viewingOffer && (
            <>
              <DialogHeader>
                <DialogTitle className="flex items-center gap-2">
                  <Award className="h-5 w-5 text-violet-bright" />
                  Offer Record Details
                </DialogTitle>
                <DialogDescription>
                  Placement offer for {viewingOffer.student?.name} ({viewingOffer.companyName})
                </DialogDescription>
              </DialogHeader>
              <div className="space-y-3.5 text-xs">
                <div className="flex items-center justify-between rounded-lg border border-border bg-muted/20 p-3">
                  <div>
                    <p className="font-semibold text-sm text-foreground">{viewingOffer.role}</p>
                    <p className="text-muted-foreground">{viewingOffer.companyName}</p>
                  </div>
                  <Badge variant={offerStatusTone[viewingOffer.status]}>{viewingOffer.status}</Badge>
                </div>

                <div className="grid grid-cols-2 gap-2.5">
                  <div className="rounded-lg border border-border p-2.5">
                    <span className="text-[10px] text-muted-foreground block">Package (CTC)</span>
                    <span className="text-sm font-semibold text-success tabular-nums">{viewingOffer.package}</span>
                  </div>
                  <div className="rounded-lg border border-border p-2.5">
                    <span className="text-[10px] text-muted-foreground block">Offer Type</span>
                    <span className="text-sm font-semibold text-foreground">{viewingOffer.offerType}</span>
                  </div>
                </div>

                <div className="space-y-2 rounded-lg border border-border p-3">
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground flex items-center gap-1.5">
                      <MapPin className="h-3.5 w-3.5 text-foreground/70" /> Location
                    </span>
                    <span className="font-medium text-foreground">{viewingOffer.location || "On-site / TBD"}</span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground flex items-center gap-1.5">
                      <Calendar className="h-3.5 w-3.5 text-foreground/70" /> Offer Extended
                    </span>
                    <span className="font-medium text-foreground">{viewingOffer.offerDate || "—"}</span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground flex items-center gap-1.5">
                      <Clock className="h-3.5 w-3.5 text-foreground/70" /> Acceptance Deadline
                    </span>
                    <span className="font-medium text-foreground">{viewingOffer.acceptanceDeadline || "—"}</span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground flex items-center gap-1.5">
                      <LogIn className="h-3.5 w-3.5 text-foreground/70" /> Tentative Joining
                    </span>
                    <span className="font-medium text-foreground">{viewingOffer.joiningDate || "—"}</span>
                  </div>
                </div>

                <div className="flex items-center justify-between rounded-lg border border-border p-2.5">
                  <span className="text-muted-foreground">Document Verification</span>
                  <Badge variant={viewingOffer.documentsVerified ? "success" : "muted"}>
                    {viewingOffer.documentsVerified ? "Verified" : "Pending Submission"}
                  </Badge>
                </div>

                <div className="flex items-center justify-between rounded-lg border border-border p-2.5">
                  <span className="text-muted-foreground">Candidate Joined</span>
                  <Badge variant={viewingOffer.joined ? "success" : "muted"}>
                    {viewingOffer.joined ? "Confirmed Joined" : "Not Joined"}
                  </Badge>
                </div>
              </div>
              <DialogFooter>
                <Button variant="outline" size="sm" onClick={() => setViewingOffer(null)}>
                  Close
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>

      {/* Revoke Offer Confirmation Dialog */}
      <Dialog open={!!revoking} onOpenChange={(open) => !open && setRevoking(null)}>
        <DialogContent className="max-w-md">
          {revoking && (
            <>
              <DialogHeader>
                <DialogTitle>Revoke Offer?</DialogTitle>
                <DialogDescription>
                  Are you sure you want to revoke the {revoking.role} offer from{" "}
                  <span className="font-semibold text-foreground">{revoking.companyName}</span> extended to{" "}
                  <span className="font-semibold text-foreground">{revoking.student?.name}</span>? The candidate will be notified.
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button variant="outline" onClick={() => setRevoking(null)} disabled={submitting}>
                  Cancel
                </Button>
                <Button
                  variant="outline"
                  className="text-risk border-risk/40 hover:bg-risk/10"
                  onClick={confirmRevoke}
                  disabled={submitting}
                >
                  {submitting ? "Revoking…" : "Revoke Offer"}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
