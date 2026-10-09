"use client";

import React, { useMemo, useState } from "react";
import {
  Loader2,
  Plus,
  Search,
  X,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  ChevronUp,
  CheckCircle2,
  AlertTriangle,
  Lightbulb,
  RotateCcw,
  Brain,
} from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import {
  addBankQuestion,
  listBankQuestions,
  setQuestionActive,
  type AddBankQuestionRequest,
  type BankQuestionResponse,
} from "@/lib/api/aptitudeBank";
import { cn } from "@/lib/utils";

// ─── Constants & Types ────────────────────────────────────────────────────────

const CATEGORIES = [
  { value: "QUANTITATIVE", label: "Quantitative Aptitude", minRequired: 15 },
  { value: "LOGICAL", label: "Logical Reasoning", minRequired: 15 },
  { value: "VERBAL", label: "Verbal Ability", minRequired: 15 },
  { value: "DATA_INTERPRETATION", label: "Data Interpretation", minRequired: 15 },
];

const OPTION_KEYS = ["A", "B", "C", "D"] as const;

/** Long-form field styled identically to Input (the ui kit ships no textarea). */
function Textarea(props: React.TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const { className, ...rest } = props;
  return (
    <textarea
      className={cn(
        "flex w-full rounded-md border border-input bg-secondary/40 px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50",
        className,
      )}
      {...rest}
    />
  );
}

const EMPTY_FORM: AddBankQuestionRequest = {
  category: "QUANTITATIVE",
  difficulty: null,
  prompt: "",
  optionA: "",
  optionB: "",
  optionC: "",
  optionD: "",
  correctOption: "A",
  explanation: null,
};

// ─── Main Component ───────────────────────────────────────────────────────────

export default function OfficerAptitudeBankPage() {
  const pushToast = useAppStore((s) => s.pushToast);

  // Category filter for the API fetch
  const [apiCategory, setApiCategory] = useState("ALL");
  const { data: rawQuestions, loading, error, reload } = useApiResource(
    () => listBankQuestions(apiCategory === "ALL" ? undefined : apiCategory),
    [apiCategory],
    [] as BankQuestionResponse[],
  );

  // Client-side search & filtering state
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<"ALL" | "ACTIVE" | "INACTIVE">("ALL");
  const [difficultyFilter, setDifficultyFilter] = useState<"ALL" | "EASY" | "MEDIUM" | "HARD">("ALL");

  // Client-side Pagination state (solves long scroll)
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Expandable question details
  const [expandedId, setExpandedId] = useState<string | null>(null);

  // Add question modal & form
  const [addOpen, setAddOpen] = useState(false);
  const [form, setForm] = useState<AddBankQuestionRequest>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [togglingIds, setTogglingIds] = useState<Set<string>>(new Set());

  // ─── Pool Health Calculations ───────────────────────────────────────────────

  const categoryStats = useMemo(() => {
    const stats: Record<string, { total: number; active: number; min: number; label: string }> = {
      QUANTITATIVE: { total: 0, active: 0, min: 15, label: "Quantitative" },
      LOGICAL: { total: 0, active: 0, min: 15, label: "Logical Reasoning" },
      VERBAL: { total: 0, active: 0, min: 15, label: "Verbal Ability" },
      DATA_INTERPRETATION: { total: 0, active: 0, min: 15, label: "Data Interpretation" },
    };

    rawQuestions.forEach((q) => {
      if (stats[q.category]) {
        stats[q.category].total += 1;
        if (q.active) stats[q.category].active += 1;
      }
    });

    return stats;
  }, [rawQuestions]);

  const blockedCategories = useMemo(() => {
    return Object.values(categoryStats).filter((s) => s.active < s.min);
  }, [categoryStats]);

  // ─── Filtered & Paginated Questions ────────────────────────────────────────

  const filteredQuestions = useMemo(() => {
    return rawQuestions.filter((q) => {
      // 1. Search Query
      if (searchQuery.trim()) {
        const query = searchQuery.toLowerCase();
        const matchesPrompt = q.prompt.toLowerCase().includes(query);
        const matchesOptions =
          q.optionA.toLowerCase().includes(query) ||
          q.optionB.toLowerCase().includes(query) ||
          q.optionC.toLowerCase().includes(query) ||
          q.optionD.toLowerCase().includes(query);
        const matchesExplanation = q.explanation?.toLowerCase().includes(query) ?? false;
        if (!matchesPrompt && !matchesOptions && !matchesExplanation) return false;
      }

      // 2. Status filter
      if (statusFilter === "ACTIVE" && !q.active) return false;
      if (statusFilter === "INACTIVE" && q.active) return false;

      // 3. Difficulty filter
      if (difficultyFilter !== "ALL") {
        const diff = (q.difficulty ?? "").toUpperCase();
        if (diff !== difficultyFilter) return false;
      }

      return true;
    });
  }, [rawQuestions, searchQuery, statusFilter, difficultyFilter]);

  // Pagination slicing
  const totalItems = filteredQuestions.length;
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  const safePage = Math.min(currentPage, totalPages);

  const paginatedQuestions = useMemo(() => {
    const startIndex = (safePage - 1) * pageSize;
    return filteredQuestions.slice(startIndex, startIndex + pageSize);
  }, [filteredQuestions, safePage, pageSize]);

  // Reset page when filters change
  const handleFilterReset = () => {
    setSearchQuery("");
    setApiCategory("ALL");
    setStatusFilter("ALL");
    setDifficultyFilter("ALL");
    setCurrentPage(1);
  };

  const hasActiveFilters =
    searchQuery.trim().length > 0 ||
    apiCategory !== "ALL" ||
    statusFilter !== "ALL" ||
    difficultyFilter !== "ALL";

  // ─── Add Question Handlers ──────────────────────────────────────────────────

  function setField<K extends keyof AddBankQuestionRequest>(key: K, value: AddBankQuestionRequest[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  const formValid =
    form.prompt.trim().length > 0 &&
    form.optionA.trim().length > 0 &&
    form.optionB.trim().length > 0 &&
    form.optionC.trim().length > 0 &&
    form.optionD.trim().length > 0;

  async function handleAdd() {
    if (!formValid) return;
    setSaving(true);
    try {
      await addBankQuestion({
        ...form,
        difficulty: form.difficulty?.trim() ? form.difficulty.trim() : null,
        explanation: form.explanation?.trim() ? form.explanation.trim() : null,
      });
      pushToast("Question added to the bank", "It is immediately eligible for official test draws.");
      setAddOpen(false);
      setForm(EMPTY_FORM);
      reload();
    } catch (err) {
      pushToast("Could not add the question", err instanceof Error ? err.message : "Try again.");
    } finally {
      setSaving(false);
    }
  }

  async function handleToggle(questionId: string, active: boolean) {
    setTogglingIds((prev) => new Set(prev).add(questionId));
    try {
      await setQuestionActive(questionId, active);
      pushToast(
        active ? "Question activated" : "Question deactivated",
        active
          ? "It is now eligible for official test draws."
          : "It will be excluded from official draws until reactivated.",
      );
      reload();
    } catch (err) {
      pushToast("Could not update the question", err instanceof Error ? err.message : "Try again.");
    } finally {
      setTogglingIds((prev) => {
        const next = new Set(prev);
        next.delete(questionId);
        return next;
      });
    }
  }

  return (
    <div className="space-y-6">
      {/* 1. Header with Add Question Action */}
      <PageHeader
        eyebrow="Placement Readiness"
        title="Aptitude Question Bank"
        subtitle="Curated MCQ repository for official placement tests. Manage categories, preview question keys, and monitor test draw thresholds."
        actions={
          <Button variant="glow" onClick={() => setAddOpen(true)} className="gap-1.5">
            <Plus className="h-4 w-4" />
            <span>Add Question</span>
          </Button>
        }
      />

      {/* 2. Category Health & Threshold Status Cards (Solves the 15/15 Threshold Blindspot) */}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {CATEGORIES.map((cat) => {
          const stats = categoryStats[cat.value] ?? { active: 0, total: 0, min: 15 };
          const isSelected = apiCategory === cat.value;
          const isBlocked = stats.active < cat.minRequired;
          const isAtLimit = stats.active === cat.minRequired;

          return (
            <Card
              key={cat.value}
              onClick={() => {
                setApiCategory(isSelected ? "ALL" : cat.value);
                setCurrentPage(1);
              }}
              className={cn(
                "cursor-pointer p-4 transition-all hover:border-violet/40",
                isSelected && "border-violet bg-violet/5 shadow-sm",
                isBlocked && "border-risk/40 bg-risk/5",
              )}
            >
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                  {cat.label}
                </span>
                <Brain className={cn("h-4 w-4", isSelected ? "text-violet-bright" : "text-muted-foreground")} />
              </div>
              <div className="mt-2.5 flex items-baseline justify-between">
                <div className="flex items-baseline gap-1.5">
                  <span className="text-2xl font-bold tracking-tight text-foreground">{stats.active}</span>
                  <span className="text-xs text-muted-foreground">/ {stats.total} Active</span>
                </div>
                <Badge
                  variant={isBlocked ? "risk" : isAtLimit ? "warning" : "success"}
                  className="text-[10px] px-1.5 py-0"
                >
                  {isBlocked
                    ? `Blocked (${stats.active}/${cat.minRequired})`
                    : isAtLimit
                      ? `Min (${cat.minRequired})`
                      : `Safe Pool`}
                </Badge>
              </div>
              <p className="mt-2 text-[11px] text-muted-foreground">
                Official test requires {cat.minRequired} questions ·{" "}
                {stats.active >= cat.minRequired ? "Eligible" : "Needs stock"}
              </p>
            </Card>
          );
        })}
      </div>

      {/* 3. Threshold Advisory or Disruption Alert */}
      {blockedCategories.length > 0 ? (
        <div className="flex items-start gap-3 rounded-lg border border-risk/40 bg-risk/10 p-3.5 text-xs text-risk">
          <AlertTriangle className="h-5 w-5 shrink-0 text-risk mt-0.5" />
          <div>
            <span className="font-semibold">Official Test Disruption Alert:</span>{" "}
            {blockedCategories.map((s) => `${s.label} (${s.active}/${s.min} active)`).join(", ")} has fewer active questions than required. Students will not be able to launch tests in these categories until questions are reactivated or added.
          </div>
        </div>
      ) : (
        <div className="flex items-center gap-2.5 rounded-lg border border-border/80 bg-surface-elevated/40 p-3 text-xs text-muted-foreground">
          <CheckCircle2 className="h-4 w-4 shrink-0 text-success" />
          <span>
            All categories meet the 15-question test draw requirement. Adding more questions expands randomization variety for students.
          </span>
        </div>
      )}

      {/* 4. Search and Multi-Filter Toolbar */}
      <Card className="p-3.5">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          {/* Search input */}
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setCurrentPage(1);
              }}
              placeholder="Search by question text, options or explanation keywords…"
              className="pl-9 pr-8"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => {
                  setSearchQuery("");
                  setCurrentPage(1);
                }}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            )}
          </div>

          {/* Filter dropdowns */}
          <div className="flex flex-wrap items-center gap-2">
            {/* Category Filter */}
            <Select
              value={apiCategory}
              onValueChange={(v) => {
                setApiCategory(v);
                setCurrentPage(1);
              }}
            >
              <SelectTrigger className="w-44 h-9 text-xs">
                <SelectValue placeholder="All Categories" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Categories</SelectItem>
                {CATEGORIES.map((c) => (
                  <SelectItem key={c.value} value={c.value}>
                    {c.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            {/* Status Filter */}
            <Select
              value={statusFilter}
              onValueChange={(v: "ALL" | "ACTIVE" | "INACTIVE") => {
                setStatusFilter(v);
                setCurrentPage(1);
              }}
            >
              <SelectTrigger className="w-32 h-9 text-xs">
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Status</SelectItem>
                <SelectItem value="ACTIVE">Active Only</SelectItem>
                <SelectItem value="INACTIVE">Deactivated</SelectItem>
              </SelectContent>
            </Select>

            {/* Reset Filters */}
            {hasActiveFilters && (
              <Button
                variant="ghost"
                size="sm"
                onClick={handleFilterReset}
                className="h-9 px-2 text-xs text-muted-foreground hover:text-foreground"
              >
                <RotateCcw className="h-3.5 w-3.5 mr-1" />
                Reset
              </Button>
            )}
          </div>
        </div>
      </Card>

      {/* 5. Question Table with Expandable Details and Pagination */}
      <AsyncBoundary
        loading={loading}
        error={error}
        empty={rawQuestions.length === 0}
        onRetry={reload}
        emptyTitle="Bank is empty"
        emptyMessage="No questions found in this category. Click 'Add Question' above to stock official tests."
      >
        <Card className="overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead className="w-10"></TableHead>
                <TableHead className="w-[45%]">Question</TableHead>
                <TableHead>Category</TableHead>
                <TableHead>Difficulty</TableHead>
                <TableHead>Correct Key</TableHead>
                <TableHead className="text-right">Active</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {paginatedQuestions.map((q) => {
                const isExpanded = expandedId === q.id;
                const categoryLabel = CATEGORIES.find((c) => c.value === q.category)?.label ?? q.category;

                return (
                  <React.Fragment key={q.id}>
                    <TableRow
                      onClick={() => setExpandedId(isExpanded ? null : q.id)}
                      className={cn(
                        "cursor-pointer transition-colors hover:bg-surface-elevated/60",
                        !q.active && "opacity-60 bg-muted/20",
                        isExpanded && "bg-surface-elevated/40 border-b-transparent",
                      )}
                    >
                      {/* Expand Toggle Chevron */}
                      <TableCell className="p-2 text-center text-muted-foreground">
                        {isExpanded ? (
                          <ChevronUp className="h-4 w-4 text-violet-bright" />
                        ) : (
                          <ChevronDown className="h-4 w-4" />
                        )}
                      </TableCell>

                      {/* Prompt */}
                      <TableCell>
                        <p className="line-clamp-2 text-sm font-medium text-foreground">{q.prompt}</p>
                        <span className="text-[11px] text-muted-foreground">Click row to preview options</span>
                      </TableCell>

                      {/* Category */}
                      <TableCell className="text-xs text-muted-foreground">{categoryLabel}</TableCell>

                      {/* Difficulty */}
                      <TableCell className="text-xs">
                        {q.difficulty ? (
                          <Badge variant="outline" className="text-[10px] capitalize">
                            {q.difficulty}
                          </Badge>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </TableCell>

                      {/* Correct Option Key */}
                      <TableCell>
                        <Badge variant="success" className="font-mono text-xs">
                          {q.correctOption}
                        </Badge>
                      </TableCell>

                      {/* Active Toggle Switch */}
                      <TableCell className="text-right" onClick={(e) => e.stopPropagation()}>
                        <Switch
                          checked={q.active}
                          disabled={togglingIds.has(q.id)}
                          onCheckedChange={(checked) => handleToggle(q.id, checked)}
                        />
                      </TableCell>
                    </TableRow>

                    {/* Expandable Preview Section */}
                    {isExpanded && (
                      <TableRow className="bg-surface-elevated/30 hover:bg-surface-elevated/30">
                        <TableCell colSpan={6} className="p-4 pt-1">
                          <div className="space-y-3 rounded-lg border border-border/80 bg-background/80 p-4">
                            <div className="flex items-center justify-between text-xs text-muted-foreground">
                              <span className="font-semibold text-foreground">Option Breakdown:</span>
                              <span>ID: {q.id}</span>
                            </div>

                            {/* 4 Options Grid with Correct Answer Highlighted */}
                            <div className="grid gap-2 sm:grid-cols-2">
                              {OPTION_KEYS.map((optKey) => {
                                const optVal = q[`option${optKey}` as keyof BankQuestionResponse] as string;
                                const isCorrect = q.correctOption === optKey;

                                return (
                                  <div
                                    key={optKey}
                                    className={cn(
                                      "flex items-start gap-2.5 rounded-md border p-2.5 text-xs",
                                      isCorrect
                                        ? "border-success/60 bg-success/10 text-foreground font-medium shadow-sm"
                                        : "border-border bg-secondary/20 text-muted-foreground",
                                    )}
                                  >
                                    <Badge
                                      variant={isCorrect ? "success" : "muted"}
                                      className="h-5 w-5 shrink-0 p-0 text-center font-mono text-xs leading-5 justify-center"
                                    >
                                      {optKey}
                                    </Badge>
                                    <span className="flex-1 text-sm">{optVal}</span>
                                    {isCorrect && (
                                      <span className="flex items-center gap-1 text-[11px] font-semibold text-success shrink-0">
                                        <CheckCircle2 className="h-3.5 w-3.5" /> Correct
                                      </span>
                                    )}
                                  </div>
                                );
                              })}
                            </div>

                            {/* Explanation if present */}
                            {q.explanation && (
                              <div className="flex items-start gap-2 rounded-md border border-violet/20 bg-violet/5 p-3 text-xs text-foreground">
                                <Lightbulb className="h-4 w-4 text-violet-bright shrink-0 mt-0.5" />
                                <div>
                                  <span className="font-semibold text-violet-bright">Explanation: </span>
                                  {q.explanation}
                                </div>
                              </div>
                            )}
                          </div>
                        </TableCell>
                      </TableRow>
                    )}
                  </React.Fragment>
                );
              })}

              {paginatedQuestions.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-xs text-muted-foreground">
                    No questions match the current search query or filter criteria.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>

          {/* 6. Pagination Footer (Solves the Long Scroll Problem completely) */}
          <div className="flex flex-col gap-3 border-t border-border px-4 py-3 sm:flex-row sm:items-center sm:justify-between text-xs text-muted-foreground">
            <div>
              Showing{" "}
              <span className="font-semibold text-foreground">
                {totalItems === 0 ? 0 : (safePage - 1) * pageSize + 1}–{Math.min(safePage * pageSize, totalItems)}
              </span>{" "}
              of <span className="font-semibold text-foreground">{totalItems}</span> questions
            </div>

            <div className="flex items-center gap-4">
              {/* Rows Per Page */}
              <div className="flex items-center gap-1.5">
                <span>Rows per page:</span>
                <Select
                  value={String(pageSize)}
                  onValueChange={(v) => {
                    setPageSize(Number(v));
                    setCurrentPage(1);
                  }}
                >
                  <SelectTrigger className="w-16 h-8 text-xs">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="10">10</SelectItem>
                    <SelectItem value="20">20</SelectItem>
                    <SelectItem value="50">50</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              {/* Prev / Next Page Buttons */}
              <div className="flex items-center gap-1">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={safePage <= 1}
                  onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                  className="h-8 w-8 p-0"
                >
                  <ChevronLeft className="h-4 w-4" />
                </Button>
                <span className="px-2 font-medium text-foreground">
                  Page {safePage} of {totalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={safePage >= totalPages}
                  onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                  className="h-8 w-8 p-0"
                >
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </div>
          </div>
        </Card>
      </AsyncBoundary>

      {/* 7. Add Question Dialog */}
      <Dialog open={addOpen} onOpenChange={setAddOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Add Bank Question</DialogTitle>
            <DialogDescription>
              Officer-added questions join the official test draw pool for your campus. Provide verified options and key — scoring is deterministic.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label>Category</Label>
                <Select value={form.category} onValueChange={(v) => setField("category", v)}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {CATEGORIES.map((c) => (
                      <SelectItem key={c.value} value={c.value}>
                        {c.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <Label>Difficulty (optional)</Label>
                <Input
                  value={form.difficulty ?? ""}
                  placeholder="e.g. Medium, Easy, Hard"
                  onChange={(e) => setField("difficulty", e.target.value)}
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <Label>Question Prompt</Label>
              <Textarea
                rows={3}
                value={form.prompt}
                placeholder="The full question text…"
                onChange={(e) => setField("prompt", e.target.value)}
              />
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              {OPTION_KEYS.map((key) => {
                const fieldKey = `option${key}` as "optionA" | "optionB" | "optionC" | "optionD";
                return (
                  <div key={key} className="space-y-1.5">
                    <Label>Option {key}</Label>
                    <Input
                      value={form[fieldKey]}
                      onChange={(e) => setField(fieldKey, e.target.value)}
                    />
                  </div>
                );
              })}
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label>Correct Option</Label>
                <Select value={form.correctOption} onValueChange={(v) => setField("correctOption", v)}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {OPTION_KEYS.map((key) => (
                      <SelectItem key={key} value={key}>
                        Option {key}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="space-y-1.5">
              <Label>Explanation (optional)</Label>
              <Textarea
                rows={2}
                value={form.explanation ?? ""}
                placeholder="Shown to students in review after they submit…"
                onChange={(e) => setField("explanation", e.target.value)}
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="ghost" onClick={() => setAddOpen(false)}>
              Cancel
            </Button>
            <Button variant="glow" disabled={!formValid || saving} onClick={handleAdd}>
              {saving && <Loader2 className="animate-spin mr-1.5 h-4 w-4" />}
              Add to Bank
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}