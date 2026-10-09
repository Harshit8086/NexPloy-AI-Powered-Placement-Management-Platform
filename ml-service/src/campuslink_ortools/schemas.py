from datetime import datetime
from pydantic import BaseModel, Field


class TimeSlot(BaseModel):
    id: str
    starts_at: datetime
    ends_at: datetime


class Venue(BaseModel):
    id: str
    capacity: int = Field(gt=0)


class Panel(BaseModel):
    id: str
    member_ids: list[str] = Field(default_factory=list)


class BlockedSlot(BaseModel):
    starts_at: datetime
    ends_at: datetime
    venue_id: str | None = None


class ScheduleEvent(BaseModel):
    event_id: str
    duration_minutes: int = Field(gt=0)
    current_slot_id: str | None = None
    current_venue_id: str | None = None
    current_panel_id: str | None = None
    student_ids: list[str] = Field(default_factory=list)
    expected_participants: int = Field(default=0, ge=0)
    candidate_slot_ids: list[str] = Field(default_factory=list)
    allowed_venue_ids: list[str] = Field(default_factory=list)
    allowed_panel_ids: list[str] = Field(default_factory=list)


class ScheduleProblem(BaseModel):
    schedule_version: str
    events: list[ScheduleEvent]
    candidate_slots: list[TimeSlot]
    venues: list[Venue]
    panels: list[Panel]
    blocked_slots: list[BlockedSlot] = Field(default_factory=list)
    time_limit_seconds: float = Field(default=5.0, gt=0, le=60)


class Alternative(BaseModel):
    rank: int
    event_id: str
    slot_id: str
    starts_at: datetime
    ends_at: datetime
    venue_id: str
    panel_id: str
    hard_violations: int
    soft_score: int
    reason_codes: list[str]
    evidence: list[str]


class ScheduleResult(BaseModel):
    status: str
    schedule_version: str
    hard_violations: int
    soft_score: int
    alternatives: list[Alternative]
    selected_assignments: list[Alternative] = Field(default_factory=list)
