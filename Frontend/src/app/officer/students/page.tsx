"use client";

import { useMemo, useRef, useState } from "react";
import Link from "next/link";
import {
  Search,
  Users,
  ShieldAlert,
  Award,
  UserPlus,
  Upload,
  Download,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  ChevronLeft,
  ChevronRight,
  Mail,
  Phone,
  GraduationCap,
  Building2,
  Calendar,
  AlertTriangle,
  ExternalLink,
  CheckCircle2,
  X,
  FileSpreadsheet,
  UserCheck,
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet";
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
import { SkillBar } from "@/components/intelligence/SkillBar";
import { ScoreRing } from "@/components/intelligence/ScoreRing";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import {
  listStudents,
  listSkillsForStudent,
  createStudent,
  updateStudentDirectory,
  setStudentActive,
  importStudentsCsv,
  type StudentDirectoryCreateInput,
  type StudentDirectoryUpdateInput,
  type CsvImportResult,
  type StudentSkill,
} from "@/lib/api/students";
import { listApplicationsByStudent, updateApplicationStatus } from "@/lib/api/applications";
import { listOffersByStudent } from "@/lib/api/offers";
import { listDrivesByCampus } from "@/lib/api/drives";
import { getCampus } from "@/lib/api/campuses";
import { listStudentInterventions } from "@/lib/api/mentors";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import {
  readinessBand,
  riskTone,
  placementStatusTone,
  applicationStatusLabel,
  applicationStatusTone,
  nextApplicationStatuses,
} from "@/lib/status";
import type { ApplicationStatus, Drive, InterventionPlan, Student } from "@/types";

/** The shared field set of the Add / Edit Directory dialogs (all strings; parsed on submit). */
interface DirectoryForm {
  name: string;
  email: string;
  rollNumber: string;
  branch: string;
  branchCode: string;
  department: string;
  batch: string;
  semester: string;
  graduationYear: string;
  yearLabel: string;
  cgpa: string;
  tenthPercentage: string;
  twelfthPercentage: string;
  backlogs: string;
  phone: string;
}

const EMPTY_DIRECTORY_FORM: DirectoryForm = {
  name: "",
  email: "",
  rollNumber: "",
  branch: "",
  branchCode: "",
  department: "",
  batch: "",
  semester: "",
  graduationYear: "",
  yearLabel: "",
  cgpa: "",
  tenthPercentage: "",
  twelfthPercentage: "",
  backlogs: "",
  phone: "",
};

type SortField =
  | "name"
  | "rollNumber"
  | "branch"
  | "cgpa"
  | "backlogs"
  | "readiness"
  | "placementProbability"
  | "riskLevel";
type SortDirection = "asc" | "desc";

/** Exports array of students to formatted CSV and triggers download */
function exportStudentsToCsv(students: Student[], filename = "students-directory.csv") {
  const headers = [
    "Roll Number",
    "Name",
    "Email",
    "Phone",
    "Branch",
    "Branch Code",
    "Department",
    "Batch",
    "Semester",
    "Graduation Year",
    "CGPA",
    "10th %",
    "12th %",
    "Backlogs",
    "Backlog History",
    "Readiness Score",
    "Placement Probability",
    "Risk Level",
    "Risk Reason",
    "Placement Status",
    "Target Role",
    "Source",
    "Status",
  ];

  const escapeCsv = (val: string | number | null | undefined) => {
    if (val == null) return "";
    const str = String(val);
    if (str.includes(",") || str.includes('"') || str.includes("\n")) {
      return `"${str.replace(/"/g, '""')}"`;
    }
    return str;
  };

  const rows = students.map((s) => [
    escapeCsv(s.rollNumber),
    escapeCsv(s.name),
    escapeCsv(s.email),
    escapeCsv(s.phone),
    escapeCsv(s.branch),
    escapeCsv(s.branchCode),
    escapeCsv(s.department),
    escapeCsv(s.batch),
    escapeCsv(s.semester || ""),
    escapeCsv(s.graduationYear || ""),
    escapeCsv(s.cgpa != null ? s.cgpa.toFixed(2) : ""),
    escapeCsv(s.tenthPercentage || ""),
    escapeCsv(s.twelfthPercentage || ""),
    escapeCsv(s.backlogs != null ? s.backlogs : ""),
    escapeCsv(s.backlogHistory != null ? s.backlogHistory : ""),
    escapeCsv(s.readiness != null ? s.readiness : ""),
    escapeCsv(s.placementProbability != null ? `${s.placementProbability}%` : ""),
    escapeCsv(s.riskLevel),
    escapeCsv(s.riskReason),
    escapeCsv(s.placementStatus),
    escapeCsv(s.targetRole),
    escapeCsv(s.source),
    escapeCsv(s.active ? "Active" : "Deactivated"),
  ]);

  const csvContent = [headers.join(","), ...rows.map((r) => r.join(","))].join("\r\n");
  const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.setAttribute("href", url);
  link.setAttribute("download", filename);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

/** Parses an optional bounded number field; returns an error message or the value. */
function parseOptionalInt(
  raw: string,
  label: string,
  min: number,
  max: number,
): { value?: number; error?: string } {
  if (raw.trim() === "") return {};
  const value = Number(raw);
  if (!Number.isInteger(value) || value < min || value > max) {
    return { error: `${label} must be a whole number between ${min} and ${max}.` };
  }
  return { value };
}

/** Parses an optional bounded decimal field; returns an error message or the value. */
function parseOptionalDecimal(
  raw: string,
  label: string,
  min: number,
  max: number,
): { value?: number; error?: string } {
  if (raw.trim() === "") return {};
  const value = Number(raw);
  if (Number.isNaN(value) || value < min || value > max) {
    return { error: `${label} must be a number between ${min} and ${max}.` };
  }
  return { value };
}

/** Visual SVG Sparkline for student readiness score progression */
function ReadinessTrendSparkline({ data }: { data: number[] }) {
  if (!data || data.length === 0) return null;
  const min = Math.min(...data, 0);
  const max = Math.max(...data, 100);
  const width = 240;
  const height = 44;
  const padding = 6;
  const innerWidth = width - padding * 2;
  const innerHeight = height - padding * 2;

  const points = data
    .map((val, idx) => {
      const x = padding + (idx / Math.max(data.length - 1, 1)) * innerWidth;
      const y = height - padding - ((val - min) / Math.max(max - min, 1)) * innerHeight;
      return `${x},${y}`;
    })
    .join(" ");

  return (
    <div className="rounded-lg border border-border/60 bg-card/40 p-3">
      <div className="mb-2 flex items-center justify-between text-xs">
        <span className="font-medium text-muted-foreground">Readiness Progression</span>
        <span className="font-semibold text-primary">{data[data.length - 1]}% (Latest)</span>
      </div>
      <svg viewBox={`0 0 ${width} ${height}`} className="h-11 w-full overflow-visible">
        <polyline
          fill="none"
          stroke="hsl(var(--primary))"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          points={points}
        />
        {data.map((val, idx) => {
          const cx = padding + (idx / Math.max(data.length - 1, 1)) * innerWidth;
          const cy = height - padding - ((val - min) / Math.max(max - min, 1)) * innerHeight;
          return (
            <circle
              key={idx}
              cx={cx}
              cy={cy}
              r="3"
              className="fill-background stroke-primary stroke-2"
            />
          );
        })}
      </svg>
      <div className="mt-1 flex justify-between text-[10px] text-muted-foreground">
        <span>Initial: {data[0]}%</span>
        <span>Peak: {Math.max(...data)}%</span>
      </div>
    </div>
  );
}

export default function OfficerStudentsPage() {
  const campusId = useAppStore((s) => s.activeCampusId);
  const pushToast = useAppStore((s) => s.pushToast);

  // Filter states
  const [query, setQuery] = useState("");
  const [branch, setBranch] = useState("all");
  const [status, setStatus] = useState("all");
  const [source, setSource] = useState("all");
  const [dirStatus, setDirStatus] = useState("all");
  const [gradYear, setGradYear] = useState("all");
  const [riskFilter, setRiskFilter] = useState("all");
  const [readinessFilter, setReadinessFilter] = useState("all");

  // Sorting state
  const [sortField, setSortField] = useState<SortField | null>(null);
  const [sortDirection, setSortDirection] = useState<SortDirection>("asc");

  // Pagination state
  const [pageSize, setPageSize] = useState<number>(25);
  const [page, setPage] = useState<number>(1);

  // Bulk actions state
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [bulkUpdating, setBulkUpdating] = useState(false);

  // Selection & modal states
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [dialog, setDialog] = useState<"add" | "edit" | null>(null);
  const [form, setForm] = useState<DirectoryForm>(EMPTY_DIRECTORY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [togglingActive, setTogglingActive] = useState(false);

  // CSV import
  const csvInputRef = useRef<HTMLInputElement>(null);
  const [importing, setImporting] = useState(false);
  const [csvResult, setCsvResult] = useState<CsvImportResult | null>(null);

  // Live roster plus campus and drives
  const { data, loading, error, reload } = useApiResource(
    async () => {
      const [students, campus, drives] = await Promise.all([
        listStudents(),
        getCampus(campusId).catch(() => undefined),
        listDrivesByCampus(campusId).catch(() => [] as Drive[]),
      ]);
      return { students, campus, drives };
    },
    [campusId],
    { students: [] as Student[], campus: undefined, drives: [] as Drive[] },
  );

  const campus = data.campus;
  const driveById = useMemo(
    () => new Map(data.drives.map((d) => [d.id, d])),
    [data.drives],
  );

  const roster = useMemo(
    () => data.students.filter((s) => s.campusId === campusId),
    [data.students, campusId],
  );

  const branches = useMemo(
    () => Array.from(new Set(roster.map((s) => s.branchCode))).sort(),
    [roster],
  );

  const gradYears = useMemo(
    () =>
      Array.from(
        new Set(roster.map((s) => s.graduationYear).filter((y): y is number => Boolean(y))),
      ).sort((a, b) => b - a),
    [roster],
  );

  // Reset page to 1 whenever any filter changes
  const handleFilterChange = (setter: (val: string) => void, val: string) => {
    setter(val);
    setPage(1);
  };

  const filtered = useMemo(() => {
    return roster
      .filter((s) => {
        if (!query) return true;
        const q = query.toLowerCase();
        return (
          s.name.toLowerCase().includes(q) ||
          (s.rollNumber || "").toLowerCase().includes(q) ||
          (s.branch || "").toLowerCase().includes(q) ||
          (s.department || "").toLowerCase().includes(q) ||
          (s.targetRole || "").toLowerCase().includes(q) ||
          (s.email || "").toLowerCase().includes(q) ||
          (s.phone || "").toLowerCase().includes(q)
        );
      })
      .filter((s) => (branch === "all" ? true : s.branchCode === branch))
      .filter((s) => (status === "all" ? true : s.placementStatus === status))
      .filter((s) => (source === "all" ? true : s.source === source))
      .filter((s) => (dirStatus === "all" ? true : dirStatus === "active" ? s.active : !s.active))
      .filter((s) => (gradYear === "all" ? true : String(s.graduationYear) === gradYear))
      .filter((s) => (riskFilter === "all" ? true : s.riskLevel === riskFilter))
      .filter((s) => {
        if (readinessFilter === "all") return true;
        if (readinessFilter === "ready") return s.readiness >= 80;
        if (readinessFilter === "developing") return s.readiness >= 50 && s.readiness < 80;
        if (readinessFilter === "intervention") return s.readiness < 50;
        return true;
      });
  }, [roster, query, branch, status, source, dirStatus, gradYear, riskFilter, readinessFilter]);

  // Sort students
  const sorted = useMemo(() => {
    if (!sortField) return filtered;
    const riskWeights: Record<string, number> = { HIGH: 3, MEDIUM: 2, LOW: 1 };
    return [...filtered].sort((a, b) => {
      let cmp = 0;
      switch (sortField) {
        case "name":
          cmp = a.name.localeCompare(b.name);
          break;
        case "rollNumber":
          cmp = (a.rollNumber || "").localeCompare(b.rollNumber || "");
          break;
        case "branch":
          cmp = (a.branchCode || "").localeCompare(b.branchCode || "");
          break;
        case "cgpa":
          cmp = a.cgpa - b.cgpa;
          break;
        case "backlogs":
          cmp = a.backlogs - b.backlogs;
          break;
        case "readiness":
          cmp = a.readiness - b.readiness;
          break;
        case "placementProbability":
          cmp = a.placementProbability - b.placementProbability;
          break;
        case "riskLevel":
          cmp = (riskWeights[a.riskLevel] ?? 0) - (riskWeights[b.riskLevel] ?? 0);
          break;
      }
      return sortDirection === "asc" ? cmp : -cmp;
    });
  }, [filtered, sortField, sortDirection]);

  // Pagination calculation
  const totalPages = Math.max(1, Math.ceil(sorted.length / pageSize));
  const currentPage = Math.min(page, totalPages);
  const paginated = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return sorted.slice(start, start + pageSize);
  }, [sorted, currentPage, pageSize]);

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      if (sortDirection === "asc") {
        setSortDirection("desc");
      } else {
        setSortField(null);
        setSortDirection("asc");
      }
    } else {
      setSortField(field);
      setSortDirection(field === "name" || field === "rollNumber" || field === "branch" ? "asc" : "desc");
    }
  };

  const selected = selectedId ? roster.find((s) => s.id === selectedId) : undefined;

  // Live per-student overlays for the detail sheet, plus student intervention plans to resolve mentor
  const { data: detail, reload: reloadDetail } = useApiResource(
    async () => {
      if (!selectedId) {
        return {
          apps: [],
          offers: [],
          skills: [] as StudentSkill[],
          interventions: [] as InterventionPlan[],
        };
      }
      const [apps, offers, skills, interventions] = await Promise.all([
        listApplicationsByStudent(selectedId),
        listOffersByStudent(selectedId),
        listSkillsForStudent(selectedId).catch(() => [] as StudentSkill[]),
        listStudentInterventions(selectedId).catch(() => [] as InterventionPlan[]),
      ]);
      return { apps, offers, skills, interventions };
    },
    [selectedId],
    {
      apps: [],
      offers: [],
      skills: [] as StudentSkill[],
      interventions: [] as InterventionPlan[],
    },
  );

  const selectedApps = detail.apps;
  const selectedOffers = detail.offers;
  const selectedSkills = detail.skills;
  const assignedMentor =
    detail.interventions.find((i) => Boolean(i.mentorId))?.mentorId ||
    selected?.mentorId ||
    null;

  const changeStage = async (
    applicationId: string,
    target: ApplicationStatus,
    driveLabel: string,
  ) => {
    try {
      await updateApplicationStatus(applicationId, target);
      pushToast("Stage updated", `${driveLabel} → ${applicationStatusLabel[target]}.`);
      reloadDetail();
      reload();
    } catch {
      pushToast("Update failed", `Could not move ${driveLabel} to ${applicationStatusLabel[target]}.`);
    }
  };

  const atRisk = roster.filter((s) => s.riskLevel === "HIGH").length;
  const placed = roster.filter(
    (s) => s.placementStatus === "PLACED" || s.placementStatus === "OFFERED",
  ).length;
  const unclaimedDirectory = roster.filter((s) => s.source === "DIRECTORY").length;

  const openAdd = () => {
    setForm(EMPTY_DIRECTORY_FORM);
    setDialog("add");
  };

  const openDirEdit = (s: Student) => {
    setForm({
      name: s.name ?? "",
      email: s.email ?? "",
      rollNumber: s.rollNumber ?? "",
      branch: s.branch ?? "",
      branchCode: s.branchCode ?? "",
      department: s.department ?? "",
      batch: s.batch ?? "",
      semester: s.semester ? String(s.semester) : "",
      graduationYear: s.graduationYear ? String(s.graduationYear) : "",
      yearLabel: s.year ?? "",
      cgpa: s.cgpa != null ? String(s.cgpa) : "",
      tenthPercentage: s.tenthPercentage ? String(s.tenthPercentage) : "",
      twelfthPercentage: s.twelfthPercentage ? String(s.twelfthPercentage) : "",
      backlogs: s.backlogs != null ? String(s.backlogs) : "",
      phone: s.phone ?? "",
    });
    setDialog("edit");
  };

  const parseDirectoryNumerics = (): {
    ok: boolean;
    semester?: number;
    graduationYear?: number;
    cgpa?: number;
    tenthPercentage?: number;
    twelfthPercentage?: number;
    backlogs?: number;
  } => {
    const semester = parseOptionalInt(form.semester, "Semester", 1, 12);
    if (semester.error) return fail(semester.error);
    const graduationYear = parseOptionalInt(form.graduationYear, "Graduation year", 1900, 2100);
    if (graduationYear.error) return fail(graduationYear.error);
    const cgpa = parseOptionalDecimal(form.cgpa, "CGPA", 0, 10);
    if (cgpa.error) return fail(cgpa.error);
    const tenthPercentage = parseOptionalDecimal(form.tenthPercentage, "10th percentage", 0, 100);
    if (tenthPercentage.error) return fail(tenthPercentage.error);
    const twelfthPercentage = parseOptionalDecimal(form.twelfthPercentage, "12th percentage", 0, 100);
    if (twelfthPercentage.error) return fail(twelfthPercentage.error);
    const backlogs = parseOptionalInt(form.backlogs, "Backlogs", 0, 1000);
    if (backlogs.error) return fail(backlogs.error);
    return {
      ok: true,
      semester: semester.value,
      graduationYear: graduationYear.value,
      cgpa: cgpa.value,
      tenthPercentage: tenthPercentage.value,
      twelfthPercentage: twelfthPercentage.value,
      backlogs: backlogs.value,
    };

    function fail(error: string): { ok: boolean } {
      pushToast("Invalid value", error);
      return { ok: false };
    }
  };

  const submitAdd = async () => {
    if (form.name.trim() === "" || form.email.trim() === "") {
      pushToast("Missing fields", "Name and email are required.");
      return;
    }
    const numerics = parseDirectoryNumerics();
    if (!numerics.ok) return;

    const input: StudentDirectoryCreateInput = {
      name: form.name.trim(),
      email: form.email.trim(),
      campusId,
      rollNumber: form.rollNumber.trim() || undefined,
      branch: form.branch.trim() || undefined,
      branchCode: form.branchCode.trim() || undefined,
      department: form.department.trim() || undefined,
      batch: form.batch.trim() || undefined,
      yearLabel: form.yearLabel.trim() || undefined,
      phone: form.phone.trim() || undefined,
      semester: numerics.semester,
      graduationYear: numerics.graduationYear,
      cgpa: numerics.cgpa,
      tenthPercentage: numerics.tenthPercentage,
      twelfthPercentage: numerics.twelfthPercentage,
      backlogs: numerics.backlogs,
    };

    setSubmitting(true);
    try {
      await createStudent(input);
      pushToast("Directory record added", `${input.name} was added to the directory.`);
      setDialog(null);
      reload();
    } catch (err) {
      pushToast("Add failed", err instanceof Error ? err.message : "Could not add the record.");
    } finally {
      setSubmitting(false);
    }
  };

  const submitDirEdit = async () => {
    if (!selected) return;
    const numerics = parseDirectoryNumerics();
    if (!numerics.ok) return;

    const input: StudentDirectoryUpdateInput = {};
    if (form.name.trim() !== (selected.name ?? "")) input.name = form.name.trim();
    if (form.email.trim().toLowerCase() !== (selected.email ?? "").toLowerCase()) {
      input.email = form.email.trim();
    }
    if (form.rollNumber.trim() !== (selected.rollNumber ?? "")) input.rollNumber = form.rollNumber.trim();
    if (form.branch.trim() !== (selected.branch ?? "")) input.branch = form.branch.trim();
    if (form.branchCode.trim() !== (selected.branchCode ?? "")) input.branchCode = form.branchCode.trim();
    if (form.department.trim() !== (selected.department ?? "")) input.department = form.department.trim();
    if (form.batch.trim() !== (selected.batch ?? "")) input.batch = form.batch.trim();
    if (form.yearLabel.trim() !== (selected.year ?? "")) input.yearLabel = form.yearLabel.trim();
    if (form.phone.trim() !== (selected.phone ?? "")) input.phone = form.phone.trim();
    if (numerics.semester !== undefined && numerics.semester !== selected.semester) {
      input.semester = numerics.semester;
    }
    if (numerics.graduationYear !== undefined && numerics.graduationYear !== selected.graduationYear) {
      input.graduationYear = numerics.graduationYear;
    }
    if (numerics.cgpa !== undefined && numerics.cgpa !== selected.cgpa) input.cgpa = numerics.cgpa;
    if (numerics.tenthPercentage !== undefined && numerics.tenthPercentage !== selected.tenthPercentage) {
      input.tenthPercentage = numerics.tenthPercentage;
    }
    if (numerics.twelfthPercentage !== undefined && numerics.twelfthPercentage !== selected.twelfthPercentage) {
      input.twelfthPercentage = numerics.twelfthPercentage;
    }
    if (numerics.backlogs !== undefined && numerics.backlogs !== selected.backlogs) {
      input.backlogs = numerics.backlogs;
    }

    if (Object.keys(input).length === 0) {
      pushToast("No changes", "Nothing was changed.");
      setDialog(null);
      return;
    }

    setSubmitting(true);
    try {
      await updateStudentDirectory(selected.id, input);
      pushToast("Directory record updated", `${selected.name}'s record was saved.`);
      setDialog(null);
      reload();
    } catch (err) {
      pushToast("Save failed", err instanceof Error ? err.message : "Could not update the record.");
    } finally {
      setSubmitting(false);
    }
  };

  const toggleActive = async (s: Student) => {
    setTogglingActive(true);
    try {
      await setStudentActive(s.id, !s.active);
      pushToast(
        s.active ? "Record deactivated" : "Record reactivated",
        s.active
          ? `${s.name} can no longer register or sign in.`
          : `${s.name} is active again.`,
      );
      reload();
    } catch (err) {
      pushToast("Update failed", err instanceof Error ? err.message : "Could not change the record.");
    } finally {
      setTogglingActive(false);
    }
  };

  const handleCsvFile = async (file: File | undefined) => {
    if (!file || importing) return;
    setImporting(true);
    try {
      const result = await importStudentsCsv(file, campusId);
      setCsvResult(result);
      reload();
    } catch (err) {
      pushToast("Import failed", err instanceof Error ? err.message : "Could not import the CSV.");
    } finally {
      setImporting(false);
      if (csvInputRef.current) csvInputRef.current.value = "";
    }
  };

  // Bulk action handlers
  const toggleSelectAll = () => {
    if (selectedIds.size === paginated.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(paginated.map((s) => s.id)));
    }
  };

  const toggleSelectOne = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleBulkDeactivate = async (targetActive: boolean) => {
    if (selectedIds.size === 0 || bulkUpdating) return;
    setBulkUpdating(true);
    try {
      const ids = Array.from(selectedIds);
      await Promise.allSettled(ids.map((id) => setStudentActive(id, targetActive)));
      pushToast(
        "Bulk update complete",
        `${ids.length} student records ${targetActive ? "reactivated" : "deactivated"}.`,
      );
      setSelectedIds(new Set());
      reload();
    } catch (err) {
      pushToast("Bulk update error", err instanceof Error ? err.message : "Failed to update records.");
    } finally {
      setBulkUpdating(false);
    }
  };

  const handleBulkExport = () => {
    const selectedStudents = roster.filter((s) => selectedIds.has(s.id));
    exportStudentsToCsv(selectedStudents, `selected-students-${selectedStudents.length}.csv`);
    pushToast("Export complete", `Exported ${selectedStudents.length} selected students.`);
  };

  const handleExportFiltered = () => {
    exportStudentsToCsv(filtered, `students-directory-${campus?.name ?? "campus"}.csv`);
    pushToast("Export complete", `Exported ${filtered.length} students to CSV.`);
  };

  const getSortIcon = (field: SortField) => {
    if (sortField !== field) {
      return <ArrowUpDown className="ml-1 h-3 w-3 text-muted-foreground/60" />;
    }
    return sortDirection === "asc" ? (
      <ArrowUp className="ml-1 h-3.5 w-3.5 text-primary" />
    ) : (
      <ArrowDown className="ml-1 h-3.5 w-3.5 text-primary" />
    );
  };

  return (
    <div className="mx-auto max-w-6xl space-y-5">
      <PageHeader
        eyebrow="Students"
        title="Student Directory"
        subtitle={`Full ${campus?.name ?? "campus"} roster with readiness, eligibility inputs, skills, and risk signals.`}
      />

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Students" value={roster.length} icon={Users} />
        <StatCard label="Directory Records" value={unclaimedDirectory} icon={UserPlus} accent="violet" />
        <StatCard label="Offered / Placed" value={placed} icon={Award} accent="success" />
        <StatCard label="High Risk" value={atRisk} icon={ShieldAlert} accent="risk" />
      </div>

      {/* Filter and action toolbar */}
      <div className="space-y-3 rounded-xl border border-border/60 bg-card/40 p-3.5 backdrop-blur-sm">
        <div className="flex flex-wrap items-center gap-3">
          <div className="relative min-w-[240px] flex-1">
            <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search name, roll, branch, email, phone or target role..."
              className="pl-8"
              value={query}
              onChange={(e) => handleFilterChange(setQuery, e.target.value)}
              aria-label="Search students"
            />
          </div>

          <Select value={gradYear} onValueChange={(v) => handleFilterChange(setGradYear, v)}>
            <SelectTrigger className="w-36" aria-label="Filter by graduation year">
              <SelectValue placeholder="Grad Year" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Batches</SelectItem>
              {gradYears.map((yr) => (
                <SelectItem key={yr} value={String(yr)}>
                  Class of {yr}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select value={branch} onValueChange={(v) => handleFilterChange(setBranch, v)}>
            <SelectTrigger className="w-36" aria-label="Filter by branch">
              <SelectValue placeholder="Branch" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Branches</SelectItem>
              {branches.map((b) => (
                <SelectItem key={b} value={b}>
                  {b}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select value={status} onValueChange={(v) => handleFilterChange(setStatus, v)}>
            <SelectTrigger className="w-40" aria-label="Filter by placement status">
              <SelectValue placeholder="Placement" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Placements</SelectItem>
              <SelectItem value="UNPLACED">Unplaced</SelectItem>
              <SelectItem value="IN PROCESS">In Process</SelectItem>
              <SelectItem value="OFFERED">Offered</SelectItem>
              <SelectItem value="PLACED">Placed</SelectItem>
            </SelectContent>
          </Select>

          <Select value={riskFilter} onValueChange={(v) => handleFilterChange(setRiskFilter, v)}>
            <SelectTrigger className="w-36" aria-label="Filter by risk level">
              <SelectValue placeholder="Risk Level" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Risk Levels</SelectItem>
              <SelectItem value="HIGH">High Risk</SelectItem>
              <SelectItem value="MEDIUM">Medium Risk</SelectItem>
              <SelectItem value="LOW">Low Risk</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3 pt-1 border-t border-border/40">
          <div className="flex flex-wrap items-center gap-2.5">
            <Select value={readinessFilter} onValueChange={(v) => handleFilterChange(setReadinessFilter, v)}>
              <SelectTrigger className="w-38" aria-label="Filter by readiness score">
                <SelectValue placeholder="Readiness" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Readiness</SelectItem>
                <SelectItem value="ready">80+ (Job Ready)</SelectItem>
                <SelectItem value="developing">50–79 (Developing)</SelectItem>
                <SelectItem value="intervention">&lt;50 (Needs Support)</SelectItem>
              </SelectContent>
            </Select>

            <Select value={source} onValueChange={(v) => handleFilterChange(setSource, v)}>
              <SelectTrigger className="w-36" aria-label="Filter by record source">
                <SelectValue placeholder="Source" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Sources</SelectItem>
                <SelectItem value="DIRECTORY">Directory</SelectItem>
                <SelectItem value="SELF_REGISTERED">Self-registered</SelectItem>
              </SelectContent>
            </Select>

            <Select value={dirStatus} onValueChange={(v) => handleFilterChange(setDirStatus, v)}>
              <SelectTrigger className="w-36" aria-label="Filter by record status">
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Records</SelectItem>
                <SelectItem value="active">Active Only</SelectItem>
                <SelectItem value="deactivated">Deactivated</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="flex items-center gap-2">
            <input
              ref={csvInputRef}
              type="file"
              accept=".csv,text/csv"
              className="hidden"
              aria-label="Upload student directory CSV"
              onChange={(e) => handleCsvFile(e.target.files?.[0])}
            />
            <Button
              variant="outline"
              size="sm"
              onClick={() => csvInputRef.current?.click()}
              disabled={importing}
              title="Import students via CSV"
            >
              <Upload className="h-3.5 w-3.5" />
              {importing ? "Importing…" : "Import CSV"}
            </Button>

            <Button
              variant="outline"
              size="sm"
              onClick={handleExportFiltered}
              disabled={filtered.length === 0}
              title="Download currently filtered students as CSV"
            >
              <Download className="h-3.5 w-3.5" />
              Export CSV
            </Button>

            <Button variant="glow" size="sm" onClick={openAdd}>
              <UserPlus className="h-3.5 w-3.5" />
              Add Student
            </Button>
          </div>
        </div>
      </div>

      {/* Bulk action toolbar */}
      {selectedIds.size > 0 && (
        <div className="flex items-center justify-between rounded-lg border border-primary/30 bg-primary/10 px-4 py-2.5 transition-all">
          <div className="flex items-center gap-2 text-sm font-medium">
            <CheckCircle2 className="h-4 w-4 text-primary" />
            <span>
              {selectedIds.size} student{selectedIds.size === 1 ? "" : "s"} selected
            </span>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={handleBulkExport}>
              <FileSpreadsheet className="h-3.5 w-3.5" />
              Export Selected
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => handleBulkDeactivate(true)}
              disabled={bulkUpdating}
            >
              Reactivate
            </Button>
            <Button
              variant="destructive"
              size="sm"
              onClick={() => handleBulkDeactivate(false)}
              disabled={bulkUpdating}
            >
              Deactivate
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setSelectedIds(new Set())}
              className="h-8 w-8 p-0"
              title="Clear selection"
            >
              <X className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={roster.length === 0}
        onRetry={reload}
        emptyTitle="No students"
        emptyMessage="No students are in this campus directory yet. Add one manually or import a CSV."
      >
        <Card className="overflow-x-auto p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-10">
                  <input
                    type="checkbox"
                    checked={paginated.length > 0 && selectedIds.size === paginated.length}
                    onChange={toggleSelectAll}
                    aria-label="Select all students on page"
                    className="h-4 w-4 cursor-pointer accent-violet-600"
                  />
                </TableHead>
                <TableHead
                  className="cursor-pointer select-none hover:text-foreground"
                  onClick={() => handleSort("name")}
                >
                  <div className="flex items-center">
                    Student {getSortIcon("name")}
                  </div>
                </TableHead>
                <TableHead
                  className="cursor-pointer select-none hover:text-foreground"
                  onClick={() => handleSort("branch")}
                >
                  <div className="flex items-center">
                    Branch {getSortIcon("branch")}
                  </div>
                </TableHead>
                <TableHead>Grad Year</TableHead>
                <TableHead>Target Role</TableHead>
                <TableHead
                  className="cursor-pointer select-none hover:text-foreground"
                  onClick={() => handleSort("cgpa")}
                >
                  <div className="flex items-center">
                    CGPA {getSortIcon("cgpa")}
                  </div>
                </TableHead>
                <TableHead
                  className="cursor-pointer select-none hover:text-foreground"
                  onClick={() => handleSort("backlogs")}
                >
                  <div className="flex items-center">
                    Backlogs {getSortIcon("backlogs")}
                  </div>
                </TableHead>
                <TableHead
                  className="cursor-pointer select-none hover:text-foreground"
                  onClick={() => handleSort("readiness")}
                >
                  <div className="flex items-center">
                    Readiness {getSortIcon("readiness")}
                  </div>
                </TableHead>
                <TableHead
                  className="cursor-pointer select-none hover:text-foreground"
                  onClick={() => handleSort("placementProbability")}
                >
                  <div className="flex items-center">
                    Probability {getSortIcon("placementProbability")}
                  </div>
                </TableHead>
                <TableHead
                  className="cursor-pointer select-none hover:text-foreground"
                  onClick={() => handleSort("riskLevel")}
                >
                  <div className="flex items-center">
                    Risk {getSortIcon("riskLevel")}
                  </div>
                </TableHead>
                <TableHead>Placement</TableHead>
                <TableHead className="text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {paginated.map((s) => (
                <TableRow
                  key={s.id}
                  className={`cursor-pointer transition-colors ${
                    selectedIds.has(s.id) ? "bg-primary/5" : ""
                  } ${s.active ? "" : "opacity-50"}`}
                  onClick={() => setSelectedId(s.id)}
                >
                  <TableCell onClick={(e) => toggleSelectOne(s.id, e)}>
                    <input
                      type="checkbox"
                      checked={selectedIds.has(s.id)}
                      onChange={() => {}}
                      aria-label={`Select ${s.name}`}
                      className="h-4 w-4 cursor-pointer accent-violet-600"
                    />
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2.5">
                      <Avatar className="h-7 w-7">
                        <AvatarFallback className="text-[10px]">{s.avatarInitials}</AvatarFallback>
                      </Avatar>
                      <div className="min-w-0">
                        <span className="flex items-center gap-1.5 font-medium">
                          {s.name}
                          {s.source === "DIRECTORY" && (
                            <Badge variant="default" className="text-[9px]">
                              DIR
                            </Badge>
                          )}
                          {!s.active && (
                            <Badge variant="muted" className="text-[9px]">
                              Deactivated
                            </Badge>
                          )}
                        </span>
                        <span className="block text-[10px] text-muted-foreground">{s.rollNumber}</span>
                      </div>
                    </div>
                  </TableCell>
                  <TableCell className="text-muted-foreground">{s.branchCode}</TableCell>
                  <TableCell className="text-muted-foreground text-xs">
                    {s.graduationYear || s.batch || "—"}
                  </TableCell>
                  <TableCell className="text-muted-foreground text-xs">{s.targetRole || "—"}</TableCell>
                  <TableCell className="tabular-nums">{s.cgpa.toFixed(2)}</TableCell>
                  <TableCell className={s.backlogs > 0 ? "tabular-nums text-risk font-medium" : "tabular-nums"}>
                    {s.backlogs}
                  </TableCell>
                  <TableCell className="tabular-nums font-medium">{s.readiness}</TableCell>
                  <TableCell className="tabular-nums">{s.placementProbability}%</TableCell>
                  <TableCell>
                    <Badge variant={riskTone[s.riskLevel]}>{s.riskLevel}</Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant={placementStatusTone[s.placementStatus]}>{s.placementStatus}</Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={(e) => {
                        e.stopPropagation();
                        setSelectedId(s.id);
                      }}
                    >
                      View
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {paginated.length === 0 && (
                <TableRow>
                  <TableCell colSpan={12} className="py-10 text-center text-sm text-muted-foreground">
                    {loading ? "Loading students…" : "No students match the current filters."}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>

          {/* Table pagination & rows per page */}
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border/60 px-4 py-3 text-xs text-muted-foreground">
            <div className="flex items-center gap-2">
              <span>Rows per page:</span>
              <Select
                value={String(pageSize)}
                onValueChange={(val) => {
                  setPageSize(Number(val));
                  setPage(1);
                }}
              >
                <SelectTrigger className="h-7 w-16 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="10">10</SelectItem>
                  <SelectItem value="25">25</SelectItem>
                  <SelectItem value="50">50</SelectItem>
                  <SelectItem value="100">100</SelectItem>
                </SelectContent>
              </Select>
              <span className="ml-2">
                Showing {sorted.length === 0 ? 0 : (currentPage - 1) * pageSize + 1} to{" "}
                {Math.min(currentPage * pageSize, sorted.length)} of {sorted.length} students
              </span>
            </div>

            <div className="flex items-center gap-2">
              <span className="text-xs">
                Page {currentPage} of {totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                className="h-7 w-7 p-0"
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                disabled={currentPage <= 1}
                aria-label="Previous page"
              >
                <ChevronLeft className="h-3.5 w-3.5" />
              </Button>
              <Button
                variant="outline"
                size="sm"
                className="h-7 w-7 p-0"
                onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                disabled={currentPage >= totalPages}
                aria-label="Next page"
              >
                <ChevronRight className="h-3.5 w-3.5" />
              </Button>
            </div>
          </div>
        </Card>
      </AsyncBoundary>

      {/* Comprehensive Student Detail Drawer */}
      <Sheet open={!!selected} onOpenChange={(open) => !open && setSelectedId(null)}>
        <SheetContent className="w-full overflow-y-auto sm:max-w-xl">
          {selected && (
            <div className="space-y-6 pb-6">
              <SheetHeader>
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <SheetTitle className="text-xl font-bold">{selected.name}</SheetTitle>
                    <SheetDescription className="mt-0.5 text-xs">
                      {selected.rollNumber} · {selected.branch || selected.branchCode}
                      {selected.batch ? ` · Batch ${selected.batch}` : ""}
                      {selected.year ? ` · ${selected.year}` : ""}
                    </SheetDescription>
                  </div>
                  <Badge variant={riskTone[selected.riskLevel]}>{selected.riskLevel} RISK</Badge>
                </div>
              </SheetHeader>

              {/* Status & Actions Box */}
              <div className="space-y-3 rounded-lg border border-border/60 bg-card/40 p-3.5">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex flex-wrap items-center gap-2 text-xs">
                    <Badge variant={placementStatusTone[selected.placementStatus]}>
                      {selected.placementStatus}
                    </Badge>
                    <Badge variant={selected.source === "DIRECTORY" ? "default" : "muted"}>
                      {selected.source === "DIRECTORY" ? "Directory record" : "Self-registered"}
                    </Badge>
                    <Badge variant={selected.active ? "success" : "muted"}>
                      {selected.active ? "Active" : "Deactivated"}
                    </Badge>
                  </div>
                  <div className="flex items-center gap-2">
                    <Button variant="outline" size="sm" onClick={() => openDirEdit(selected)}>
                      Edit Details
                    </Button>
                    <Button
                      variant={selected.active ? "outline" : "glow"}
                      size="sm"
                      onClick={() => toggleActive(selected)}
                      disabled={togglingActive}
                    >
                      {togglingActive
                        ? "Updating…"
                        : selected.active
                          ? "Deactivate"
                          : "Reactivate"}
                    </Button>
                  </div>
                </div>
                <p className="text-[11px] text-muted-foreground">
                  Officer-managed academic data is authoritative.{" "}
                  {selected.active
                    ? "Deactivating disables account login and placement eligibility."
                    : "This record is currently deactivated."}
                </p>
              </div>

              {/* Contact & Academic Information Grid */}
              <div className="space-y-2.5">
                <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Contact & Academic Profile
                </p>
                <div className="grid grid-cols-2 gap-2.5 rounded-lg border border-border/60 bg-card/30 p-3.5 text-xs sm:grid-cols-3">
                  <div>
                    <span className="flex items-center gap-1 text-[11px] text-muted-foreground">
                      <Mail className="h-3 w-3" /> Email
                    </span>
                    <a
                      href={`mailto:${selected.email}`}
                      className="mt-0.5 block truncate font-medium text-foreground hover:underline"
                      title={selected.email}
                    >
                      {selected.email}
                    </a>
                  </div>
                  <div>
                    <span className="flex items-center gap-1 text-[11px] text-muted-foreground">
                      <Phone className="h-3 w-3" /> Phone
                    </span>
                    {selected.phone ? (
                      <a
                        href={`tel:${selected.phone}`}
                        className="mt-0.5 block font-medium text-foreground hover:underline"
                      >
                        {selected.phone}
                      </a>
                    ) : (
                      <span className="mt-0.5 block font-medium text-muted-foreground">—</span>
                    )}
                  </div>
                  <div>
                    <span className="flex items-center gap-1 text-[11px] text-muted-foreground">
                      <Building2 className="h-3 w-3" /> Department
                    </span>
                    <span className="mt-0.5 block font-medium text-foreground">
                      {selected.department || selected.branch || "—"}
                    </span>
                  </div>
                  <div>
                    <span className="flex items-center gap-1 text-[11px] text-muted-foreground">
                      <Calendar className="h-3 w-3" /> Semester
                    </span>
                    <span className="mt-0.5 block font-medium text-foreground">
                      {selected.semester ? `Semester ${selected.semester}` : "—"}
                    </span>
                  </div>
                  <div>
                    <span className="flex items-center gap-1 text-[11px] text-muted-foreground">
                      <GraduationCap className="h-3 w-3" /> Grad Year
                    </span>
                    <span className="mt-0.5 block font-medium text-foreground">
                      {selected.graduationYear || selected.batch || "—"}
                    </span>
                  </div>
                  <div>
                    <span className="text-[11px] text-muted-foreground">10th / 12th %</span>
                    <span className="mt-0.5 block font-medium text-foreground">
                      {selected.tenthPercentage > 0 ? `${selected.tenthPercentage.toFixed(1)}%` : "—"} /{" "}
                      {selected.twelfthPercentage > 0 ? `${selected.twelfthPercentage.toFixed(1)}%` : "—"}
                    </span>
                  </div>
                  <div>
                    <span className="text-[11px] text-muted-foreground">Backlogs (Current / Hist.)</span>
                    <span className="mt-0.5 block font-medium text-foreground">
                      {selected.backlogs} current · {selected.backlogHistory || 0} history
                    </span>
                  </div>
                  <div>
                    <span className="flex items-center gap-1 text-[11px] text-muted-foreground">
                      <UserCheck className="h-3 w-3" /> Mentor
                    </span>
                    <span className="mt-0.5 block truncate font-medium text-foreground" title={assignedMentor ?? undefined}>
                      {assignedMentor ? assignedMentor : "Not assigned"}
                    </span>
                  </div>
                  <div>
                    <span className="text-[11px] text-muted-foreground">Target Role</span>
                    <span className="mt-0.5 block truncate font-medium text-foreground">
                      {selected.targetRole || "Not specified"}
                    </span>
                  </div>
                </div>
              </div>

              {/* Risk Alert Callout */}
              {(selected.riskLevel === "HIGH" || selected.riskLevel === "MEDIUM" || selected.riskReason) && (
                <div
                  className={`rounded-lg border p-3 text-xs ${
                    selected.riskLevel === "HIGH"
                      ? "border-risk/40 bg-risk/10 text-risk"
                      : "border-warning/40 bg-warning/10 text-warning"
                  }`}
                >
                  <div className="flex items-center gap-1.5 font-semibold">
                    <AlertTriangle className="h-4 w-4 shrink-0" />
                    <span>Risk Alert: {selected.riskLevel} Support Need</span>
                  </div>
                  <p className="mt-1 text-[11px] leading-relaxed text-foreground/90">
                    {selected.riskReason ||
                      "This student has been flagged due to low assessment scores or academic backlog risk."}
                  </p>
                </div>
              )}

              {/* Readiness Intelligence & Trend */}
              <div className="space-y-3">
                <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Readiness Intelligence
                </p>
                <div className="flex flex-wrap items-center gap-5 rounded-lg border border-border/60 bg-card/30 p-3.5">
                  <ScoreRing value={selected.readiness} size={105} strokeWidth={8} label="Readiness" />
                  <div className="space-y-1.5 text-xs">
                    <p className="text-muted-foreground">
                      CGPA: <span className="font-semibold text-foreground">{selected.cgpa.toFixed(2)}</span>
                    </p>
                    <p className="text-muted-foreground">
                      Placement Probability:{" "}
                      <span className="font-semibold text-foreground">
                        {selected.placementProbability}%
                      </span>
                    </p>
                    <p className="text-muted-foreground">
                      Active Applications:{" "}
                      <span className="font-semibold text-foreground">
                        {selectedApps.filter((a) => a.status !== "eligible").length}
                      </span>
                    </p>
                    <p className="text-muted-foreground">
                      Confirmed Offers:{" "}
                      <span className="font-semibold text-foreground">{selectedOffers.length}</span>
                    </p>
                    <div className="pt-1">
                      <Badge variant={readinessBand(selected.readiness).tone}>
                        {readinessBand(selected.readiness).label}
                      </Badge>
                    </div>
                  </div>
                </div>

                {/* Sparkline trend if available */}
                {selected.readinessTrend && selected.readinessTrend.length > 0 && (
                  <ReadinessTrendSparkline data={selected.readinessTrend} />
                )}
              </div>

              {/* 5-Factor Readiness Breakdown */}
              <div className="space-y-2.5">
                <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Readiness Dimensions
                </p>
                <div className="space-y-2.5 rounded-lg border border-border/60 bg-card/30 p-3.5">
                  <SkillBar label="Academics" value={selected.breakdown.academic} />
                  <SkillBar label="Aptitude" value={selected.breakdown.aptitude} />
                  <SkillBar label="Communication" value={selected.breakdown.communication} />
                  <SkillBar label="Projects" value={selected.breakdown.projects} />
                  <SkillBar label="Mock Interview" value={selected.breakdown.interview} />
                </div>
              </div>

              {/* Raw Assessment Scores */}
              {selected.assessments && (
                <div className="space-y-2">
                  <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                    Assessment Scores
                  </p>
                  <div className="grid grid-cols-2 gap-2 text-center sm:grid-cols-5">
                    <div className="rounded-md border border-border/60 bg-card/30 p-2">
                      <div className="text-[10px] text-muted-foreground">Aptitude</div>
                      <div className="text-sm font-semibold">{selected.assessments.aptitude}%</div>
                    </div>
                    <div className="rounded-md border border-border/60 bg-card/30 p-2">
                      <div className="text-[10px] text-muted-foreground">Coding</div>
                      <div className="text-sm font-semibold">{selected.assessments.coding}%</div>
                    </div>
                    <div className="rounded-md border border-border/60 bg-card/30 p-2">
                      <div className="text-[10px] text-muted-foreground">Technical</div>
                      <div className="text-sm font-semibold">{selected.assessments.technical}%</div>
                    </div>
                    <div className="rounded-md border border-border/60 bg-card/30 p-2">
                      <div className="text-[10px] text-muted-foreground">Comm.</div>
                      <div className="text-sm font-semibold">{selected.assessments.communication}%</div>
                    </div>
                    <div className="rounded-md border border-border/60 bg-card/30 p-2">
                      <div className="text-[10px] text-muted-foreground">Interview</div>
                      <div className="text-sm font-semibold">{selected.assessments.mockInterview}%</div>
                    </div>
                  </div>
                </div>
              )}

              {/* Career Preferences */}
              {(selected.preferredRoles.length > 0 ||
                selected.preferredDomains.length > 0 ||
                selected.preferredLocations.length > 0) && (
                <div className="space-y-2 rounded-lg border border-border/60 bg-card/30 p-3 text-xs">
                  <p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Preferences & Aspirations
                  </p>
                  {selected.preferredRoles.length > 0 && (
                    <div className="flex flex-wrap items-center gap-1.5">
                      <span className="text-muted-foreground">Roles:</span>
                      {selected.preferredRoles.map((r) => (
                        <Badge key={r} variant="outline" className="text-[10px]">
                          {r}
                        </Badge>
                      ))}
                    </div>
                  )}
                  {selected.preferredDomains.length > 0 && (
                    <div className="flex flex-wrap items-center gap-1.5">
                      <span className="text-muted-foreground">Domains:</span>
                      {selected.preferredDomains.map((d) => (
                        <Badge key={d} variant="outline" className="text-[10px]">
                          {d}
                        </Badge>
                      ))}
                    </div>
                  )}
                  {selected.preferredLocations.length > 0 && (
                    <div className="flex flex-wrap items-center gap-1.5">
                      <span className="text-muted-foreground">Locations:</span>
                      {selected.preferredLocations.map((l) => (
                        <Badge key={l} variant="outline" className="text-[10px]">
                          {l}
                        </Badge>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* Applications & Drive Skill Fit */}
              <div className="space-y-2.5">
                <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Drive Applications ({selectedApps.filter((a) => a.status !== "eligible").length})
                </p>
                {selectedApps.filter((a) => a.status !== "eligible").length > 0 ? (
                  <div className="space-y-2">
                    {selectedApps
                      .filter((a) => a.status !== "eligible")
                      .map((app) => {
                        const drive = driveById.get(app.driveId);
                        const driveLabel = drive
                          ? `${drive.companyName} — ${drive.role}`
                          : app.driveId;
                        const nextStatuses = nextApplicationStatuses(app.status);
                        return (
                          <div
                            key={app.id}
                            className="rounded-lg border border-border/60 bg-card/30 p-3 space-y-2"
                          >
                            <div className="flex flex-wrap items-center justify-between gap-2">
                              <div className="min-w-0">
                                <span className="block truncate text-sm font-medium">{driveLabel}</span>
                                <Badge
                                  variant={applicationStatusTone[app.status]}
                                  className="mt-1 text-[10px]"
                                >
                                  {applicationStatusLabel[app.status]}
                                </Badge>
                              </div>
                              {nextStatuses.length > 0 && (
                                <Select
                                  value=""
                                  onValueChange={(target) =>
                                    changeStage(app.id, target as ApplicationStatus, driveLabel)
                                  }
                                >
                                  <SelectTrigger
                                    className="h-8 w-[9.5rem] text-xs"
                                    aria-label={`Change stage for ${driveLabel}`}
                                  >
                                    <SelectValue placeholder="Move to…" />
                                  </SelectTrigger>
                                  <SelectContent>
                                    {nextStatuses.map((s) => (
                                      <SelectItem key={s} value={s}>
                                        {applicationStatusLabel[s]}
                                      </SelectItem>
                                    ))}
                                  </SelectContent>
                                </Select>
                              )}
                            </div>
                            <div className="pt-1 flex items-center justify-between border-t border-border/40 text-[11px]">
                              <Link
                                href={`/student/skill-gap?driveId=${app.driveId}`}
                                className="inline-flex items-center gap-1 text-primary hover:underline"
                              >
                                <span>Check Drive Skill Fit</span>
                                <ExternalLink className="h-3 w-3" />
                              </Link>
                              {drive?.package && (
                                <span className="text-muted-foreground">{drive.package}</span>
                              )}
                            </div>
                          </div>
                        );
                      })}
                  </div>
                ) : (
                  <p className="text-xs text-muted-foreground rounded-lg border border-border/40 p-3">
                    This student hasn&apos;t applied to any drive yet.
                  </p>
                )}
              </div>

              {/* Skills & Verification */}
              <div className="space-y-2.5">
                <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Skills & Proficiencies
                </p>
                {selectedSkills.length > 0 ? (
                  <div className="flex flex-wrap gap-1.5">
                    {selectedSkills.map((sk) => (
                      <Badge
                        key={sk.id}
                        variant={
                          sk.verificationStatus === "VERIFIED"
                            ? "success"
                            : sk.verificationStatus === "EVIDENCED"
                              ? "default"
                              : "muted"
                        }
                        className="text-xs"
                        title={
                          sk.verificationStatus === "VERIFIED"
                            ? "Verified via skill viva"
                            : sk.verificationStatus === "EVIDENCED"
                              ? "Backed by a proof link"
                              : "Self-declared (unverified)"
                        }
                      >
                        {sk.skill} · {sk.proficiency}%
                        {sk.verificationStatus === "VERIFIED" ? " ✓" : ""}
                      </Badge>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-muted-foreground rounded-lg border border-border/40 p-3">
                    No verified profile skills recorded yet.
                  </p>
                )}

                {(selected.strengths.length > 0 || selected.criticalGaps.length > 0) && (
                  <div className="mt-3 space-y-2">
                    <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                      Resume Analysis Signals
                    </p>
                    <div className="flex flex-wrap gap-1.5">
                      {selected.strengths.map((sk) => (
                        <Badge key={sk} variant="success" className="text-xs">
                          {sk}
                        </Badge>
                      ))}
                      {selected.criticalGaps.map((sk) => (
                        <Badge key={sk} variant="risk" className="text-xs">
                          {sk}
                        </Badge>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {/* Projects, Experience & Certifications */}
              {((selected.certifications?.length ?? 0) > 0 ||
                (selected.projects?.length ?? 0) > 0 ||
                (selected.experience?.length ?? 0) > 0) && (
                <div className="space-y-3">
                  <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                    Projects & Experience
                  </p>

                  {selected.certifications && selected.certifications.length > 0 && (
                    <div className="space-y-1.5">
                      <span className="text-[11px] text-muted-foreground">Certifications</span>
                      <div className="flex flex-wrap gap-1.5">
                        {selected.certifications.map((c) => (
                          <Badge key={c} variant="default" className="text-xs">
                            <Award className="mr-1 h-3 w-3" />
                            {c}
                          </Badge>
                        ))}
                      </div>
                    </div>
                  )}

                  {selected.projects && selected.projects.length > 0 && (
                    <div className="space-y-2">
                      <span className="text-[11px] text-muted-foreground">
                        Projects ({selected.projects.length})
                      </span>
                      {selected.projects.map((proj, idx) => (
                        <div key={idx} className="rounded-lg border border-border/60 bg-card/30 p-2.5 text-xs">
                          <div className="flex items-center justify-between font-medium">
                            <span className="text-foreground">{proj.name}</span>
                            {proj.githubUrl && (
                              <a
                                href={proj.githubUrl}
                                target="_blank"
                                rel="noreferrer"
                                className="text-muted-foreground hover:text-foreground"
                              >
                                <ExternalLink className="h-3 w-3" />
                              </a>
                            )}
                          </div>
                          {proj.summary && (
                            <p className="mt-1 text-[11px] text-muted-foreground line-clamp-2">
                              {proj.summary}
                            </p>
                          )}
                          {proj.stack && proj.stack.length > 0 && (
                            <div className="mt-1.5 flex flex-wrap gap-1">
                              {proj.stack.map((stk) => (
                                <Badge key={stk} variant="muted" className="text-[9px]">
                                  {stk}
                                </Badge>
                              ))}
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}

                  {selected.experience && selected.experience.length > 0 && (
                    <div className="space-y-2">
                      <span className="text-[11px] text-muted-foreground">
                        Work Experience ({selected.experience.length})
                      </span>
                      {selected.experience.map((exp, idx) => (
                        <div key={idx} className="rounded-lg border border-border/60 bg-card/30 p-2.5 text-xs">
                          <div className="flex items-center justify-between font-medium text-foreground">
                            <span>
                              {exp.role} · {exp.company}
                            </span>
                            <span className="text-[10px] text-muted-foreground">{exp.duration}</span>
                          </div>
                          {exp.summary && (
                            <p className="mt-1 text-[11px] text-muted-foreground">{exp.summary}</p>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </SheetContent>
      </Sheet>

      {/* Add / Edit directory record modal */}
      <Dialog open={dialog !== null} onOpenChange={(open) => !open && setDialog(null)}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>
              {dialog === "add" ? "Add Directory Record" : "Edit Directory Details"}
            </DialogTitle>
            <DialogDescription>
              {dialog === "add"
                ? "Add one student to the campus directory. The student claims this record by registering with the same email."
                : selected
                  ? `Update ${selected.name}'s officer-owned record. Email cannot change once the student has registered.`
                  : "Update the student's officer-owned record."}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="dir-name">Full Name *</Label>
                <Input
                  id="dir-name"
                  value={form.name}
                  onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="dir-email">Email *</Label>
                <Input
                  id="dir-email"
                  type="email"
                  value={form.email}
                  onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="dir-roll">Roll Number</Label>
                <Input
                  id="dir-roll"
                  value={form.rollNumber}
                  onChange={(e) => setForm((f) => ({ ...f, rollNumber: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="dir-branch">Branch</Label>
                <Input
                  id="dir-branch"
                  value={form.branch}
                  onChange={(e) => setForm((f) => ({ ...f, branch: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="dir-branchCode">Branch Code</Label>
                <Input
                  id="dir-branchCode"
                  value={form.branchCode}
                  onChange={(e) => setForm((f) => ({ ...f, branchCode: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="dir-dept">Department</Label>
                <Input
                  id="dir-dept"
                  value={form.department}
                  onChange={(e) => setForm((f) => ({ ...f, department: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="dir-batch">Batch</Label>
                <Input
                  id="dir-batch"
                  value={form.batch}
                  onChange={(e) => setForm((f) => ({ ...f, batch: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="dir-semester">Semester (1–12)</Label>
                <Input
                  id="dir-semester"
                  type="number"
                  min={1}
                  max={12}
                  value={form.semester}
                  onChange={(e) => setForm((f) => ({ ...f, semester: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="dir-grad">Graduation Year</Label>
                <Input
                  id="dir-grad"
                  type="number"
                  value={form.graduationYear}
                  onChange={(e) => setForm((f) => ({ ...f, graduationYear: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="dir-year">Year Label</Label>
                <Input
                  id="dir-year"
                  value={form.yearLabel}
                  placeholder="e.g. 3rd Year"
                  onChange={(e) => setForm((f) => ({ ...f, yearLabel: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="dir-cgpa">CGPA (0–10)</Label>
                <Input
                  id="dir-cgpa"
                  type="number"
                  step="0.01"
                  min={0}
                  max={10}
                  value={form.cgpa}
                  onChange={(e) => setForm((f) => ({ ...f, cgpa: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="dir-backlogs">Backlogs</Label>
                <Input
                  id="dir-backlogs"
                  type="number"
                  min={0}
                  value={form.backlogs}
                  onChange={(e) => setForm((f) => ({ ...f, backlogs: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="dir-tenth">10th % (0–100)</Label>
                <Input
                  id="dir-tenth"
                  type="number"
                  step="0.01"
                  min={0}
                  max={100}
                  value={form.tenthPercentage}
                  onChange={(e) => setForm((f) => ({ ...f, tenthPercentage: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="dir-twelfth">12th % (0–100)</Label>
                <Input
                  id="dir-twelfth"
                  type="number"
                  step="0.01"
                  min={0}
                  max={100}
                  value={form.twelfthPercentage}
                  onChange={(e) => setForm((f) => ({ ...f, twelfthPercentage: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="dir-phone">Phone</Label>
                <Input
                  id="dir-phone"
                  value={form.phone}
                  onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
                />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialog(null)} disabled={submitting}>
              Cancel
            </Button>
            <Button
              variant="glow"
              onClick={dialog === "add" ? submitAdd : submitDirEdit}
              disabled={submitting}
            >
              {submitting ? "Saving…" : dialog === "add" ? "Add Record" : "Save Changes"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* CSV import result summary modal */}
      <Dialog open={csvResult !== null} onOpenChange={(open) => !open && setCsvResult(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>CSV Import Complete</DialogTitle>
            <DialogDescription>
              {csvResult
                ? `${csvResult.imported} record${csvResult.imported === 1 ? "" : "s"} imported, ${csvResult.skipped} skipped.`
                : ""}
            </DialogDescription>
          </DialogHeader>
          {csvResult && csvResult.errors.length > 0 && (
            <div className="max-h-64 space-y-1.5 overflow-y-auto">
              {csvResult.errors.map((e) => (
                <p key={e.row} className="rounded-md border border-risk/20 bg-risk/5 px-2.5 py-1.5 text-xs">
                  <span className="font-medium">Row {e.row}:</span> {e.reason}
                </p>
              ))}
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setCsvResult(null)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}