from dataclasses import dataclass
from datetime import datetime

from ortools.sat.python import cp_model

from campuslink_ortools.schemas import Alternative, ScheduleEvent, ScheduleProblem, ScheduleResult


@dataclass(frozen=True)
class Option:
    event: ScheduleEvent
    slot_id: str
    starts_at: datetime
    ends_at: datetime
    venue_id: str
    panel_id: str
    venue_capacity: int
    panel_members: frozenset[str]


def optimize(problem: ScheduleProblem) -> ScheduleResult:
    options = _build_options(problem)
    model = cp_model.CpModel()
    variables = [model.new_bool_var(f"assign_{index}") for index in range(len(options))]

    for event in problem.events:
        event_variables = [
            variables[index] for index, option in enumerate(options) if option.event.event_id == event.event_id
        ]
        if not event_variables:
            return _result(problem, "INFEASIBLE", [], [])
        model.add_exactly_one(event_variables)

    for left_index, left in enumerate(options):
        for right_index in range(left_index + 1, len(options)):
            right = options[right_index]
            if left.event.event_id == right.event.event_id:
                continue
            if not _overlaps(left.starts_at, left.ends_at, right.starts_at, right.ends_at):
                continue
            if left.venue_id == right.venue_id:
                model.add(variables[left_index] + variables[right_index] <= 1)
            if left.panel_members.intersection(right.panel_members):
                model.add(variables[left_index] + variables[right_index] <= 1)
            if set(left.event.student_ids).intersection(right.event.student_ids):
                model.add(variables[left_index] + variables[right_index] <= 1)

    penalties = []
    for index, option in enumerate(options):
        changed = int(
            option.slot_id != option.event.current_slot_id
            or option.venue_id != option.event.current_venue_id
            or option.panel_id != option.event.current_panel_id
        )
        penalties.append((variables[index], changed * 100 + _slot_penalty(option)))
    model.minimize(sum(variable * penalty for variable, penalty in penalties))

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = problem.time_limit_seconds
    solver.parameters.num_search_workers = 1
    status = solver.solve(model)
    status_name = solver.status_name(status)
    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return _result(problem, status_name, [], [])

    selected = [options[index] for index, variable in enumerate(variables) if solver.value(variable)]
    selected_assignments = [_alternative(option, 1, problem) for option in selected]
    alternatives = _rank_alternatives(selected, problem)
    return _result(problem, status_name, selected_assignments, alternatives, solver.objective_value)


def _build_options(problem: ScheduleProblem) -> list[Option]:
    slots = {slot.id: slot for slot in problem.candidate_slots}
    venues = {venue.id: venue for venue in problem.venues}
    panels = {panel.id: panel for panel in problem.panels}
    options: list[Option] = []
    for event in problem.events:
        slot_ids = event.candidate_slot_ids or list(slots)
        venue_ids = event.allowed_venue_ids or list(venues)
        panel_ids = event.allowed_panel_ids or list(panels)
        for slot_id in slot_ids:
            slot = slots.get(slot_id)
            if slot is None:
                continue
            for venue_id in venue_ids:
                venue = venues.get(venue_id)
                if venue is None or venue.capacity < event.expected_participants:
                    continue
                if _blocked(slot.starts_at, slot.ends_at, venue.id, problem):
                    continue
                for panel_id in panel_ids:
                    panel = panels.get(panel_id)
                    if panel is None:
                        continue
                    options.append(Option(
                        event, slot.id, slot.starts_at, slot.ends_at, venue.id, panel.id,
                        venue.capacity, frozenset(panel.member_ids)
                    ))
    return options


def _blocked(starts_at: datetime, ends_at: datetime, venue_id: str, problem: ScheduleProblem) -> bool:
    for block in problem.blocked_slots:
        if block.venue_id is not None and block.venue_id != venue_id:
            continue
        if _overlaps(starts_at, ends_at, block.starts_at, block.ends_at):
            return True
    return False


def _overlaps(first_start: datetime, first_end: datetime, second_start: datetime, second_end: datetime) -> bool:
    return first_start < second_end and second_start < first_end


def _slot_penalty(option: Option) -> int:
    return option.starts_at.hour * 2 + option.starts_at.minute // 30


def _alternative(option: Option, rank: int, problem: ScheduleProblem) -> Alternative:
    event = option.event
    reasons = []
    evidence = []
    if option.slot_id != event.current_slot_id:
        reasons.append("TIME_CHANGED")
        evidence.append("Candidate slot differs from the current schedule")
    if option.venue_id != event.current_venue_id:
        reasons.append("VENUE_CHANGED")
        evidence.append("Selected venue satisfies capacity and overlap constraints")
    if option.panel_id != event.current_panel_id:
        reasons.append("PANEL_CHANGED")
        evidence.append("Selected panel has no overlapping member assignment")
    if not reasons:
        reasons.append("CURRENT_ASSIGNMENT_VALID")
        evidence.append("Current assignment remains valid")
    return Alternative(
        rank=rank,
        event_id=event.event_id,
        slot_id=option.slot_id,
        starts_at=option.starts_at,
        ends_at=option.ends_at,
        venue_id=option.venue_id,
        panel_id=option.panel_id,
        hard_violations=0,
        soft_score=-(_slot_penalty(option) + len(reasons) * 100),
        reason_codes=reasons,
        evidence=evidence,
    )


def _rank_alternatives(selected: list[Option], problem: ScheduleProblem) -> list[Alternative]:
    return [_alternative(option, index + 1, problem) for index, option in enumerate(selected)]


def _result(
    problem: ScheduleProblem,
    status: str,
    selected: list[Alternative],
    alternatives: list[Alternative],
    soft_score: float = 0,
) -> ScheduleResult:
    return ScheduleResult(
        status=status,
        schedule_version=problem.schedule_version,
        hard_violations=0 if status in {"OPTIMAL", "FEASIBLE"} else -1,
        soft_score=int(-soft_score),
        alternatives=alternatives,
        selected_assignments=selected,
    )
