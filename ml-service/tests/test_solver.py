from datetime import datetime, timezone

from campuslink_ortools.schemas import BlockedSlot, Panel, ScheduleEvent, ScheduleProblem, TimeSlot, Venue
from campuslink_ortools.solver import optimize


def instant(hour: int) -> datetime:
    return datetime(2026, 9, 22, hour, 0, tzinfo=timezone.utc)


def test_solver_moves_overlapping_events_to_available_resources() -> None:
    problem = ScheduleProblem(
        schedule_version="17",
        candidate_slots=[
            TimeSlot(id="morning", starts_at=instant(10), ends_at=instant(12)),
            TimeSlot(id="afternoon", starts_at=instant(14), ends_at=instant(16)),
        ],
        venues=[Venue(id="lab-2", capacity=80), Venue(id="lab-3", capacity=60)],
        panels=[Panel(id="panel-a", member_ids=["member-1"]), Panel(id="panel-c", member_ids=["member-2"])],
        events=[
            ScheduleEvent(
                event_id="technova",
                duration_minutes=120,
                current_slot_id="morning",
                current_venue_id="lab-2",
                current_panel_id="panel-a",
                expected_participants=40,
                candidate_slot_ids=["morning"],
                allowed_venue_ids=["lab-2"],
                allowed_panel_ids=["panel-a"],
            ),
            ScheduleEvent(
                event_id="cloudsphere",
                duration_minutes=120,
                current_slot_id="morning",
                current_venue_id="lab-2",
                current_panel_id="panel-a",
                expected_participants=30,
                candidate_slot_ids=["morning", "afternoon"],
                allowed_venue_ids=["lab-2"],
                allowed_panel_ids=["panel-a"],
            ),
        ],
    )

    result = optimize(problem)

    assert result.status in {"OPTIMAL", "FEASIBLE"}
    assert result.hard_violations == 0
    cloud = next(item for item in result.selected_assignments if item.event_id == "cloudsphere")
    assert cloud.slot_id == "afternoon"
    assert cloud.reason_codes


def test_solver_avoids_blocked_venue_but_uses_free_venue_at_same_time() -> None:
    problem = ScheduleProblem(
        schedule_version="19",
        candidate_slots=[TimeSlot(id="morning", starts_at=instant(10), ends_at=instant(12))],
        venues=[Venue(id="lab-2", capacity=80), Venue(id="lab-3", capacity=60)],
        panels=[Panel(id="panel-a", member_ids=["member-1"])],
        blocked_slots=[BlockedSlot(starts_at=instant(9), ends_at=instant(13), venue_id="lab-2")],
        events=[
            ScheduleEvent(
                event_id="technova",
                duration_minutes=120,
                current_slot_id="morning",
                current_venue_id="lab-2",
                current_panel_id="panel-a",
                expected_participants=40,
                candidate_slot_ids=["morning"],
                allowed_venue_ids=["lab-2", "lab-3"],
                allowed_panel_ids=["panel-a"],
            ),
        ],
    )

    result = optimize(problem)

    assert result.status in {"OPTIMAL", "FEASIBLE"}
    assert result.hard_violations == 0
    assignment = result.selected_assignments[0]
    assert assignment.venue_id == "lab-3"


def test_solver_reports_infeasible_when_no_candidate_slot_exists() -> None:
    problem = ScheduleProblem(
        schedule_version="18",
        candidate_slots=[],
        venues=[Venue(id="lab-2", capacity=80)],
        panels=[Panel(id="panel-a", member_ids=["member-1"])],
        events=[ScheduleEvent(event_id="drive", duration_minutes=120, expected_participants=10)],
    )

    result = optimize(problem)

    assert result.status == "INFEASIBLE"
    assert result.hard_violations == -1
