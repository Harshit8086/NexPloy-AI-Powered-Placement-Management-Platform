package com.nexploy.campuslink.analytics.service;

import com.nexploy.campuslink.analytics.dto.DashboardAnalyticsResponse;
import com.nexploy.campuslink.analytics.dto.DashboardAnalyticsResponse.Alert;
import com.nexploy.campuslink.analytics.dto.DashboardAnalyticsResponse.BandCount;
import com.nexploy.campuslink.analytics.dto.DashboardAnalyticsResponse.BranchStat;
import com.nexploy.campuslink.analytics.dto.DashboardAnalyticsResponse.FunnelStage;
import com.nexploy.campuslink.analytics.dto.DashboardAnalyticsResponse.LevelCount;
import com.nexploy.campuslink.analytics.dto.DashboardAnalyticsResponse.MonthCount;
import com.nexploy.campuslink.analytics.dto.DashboardAnalyticsResponse.RecruiterEngagement;
import com.nexploy.campuslink.analytics.dto.DashboardAnalyticsResponse.SalaryPoint;
import com.nexploy.campuslink.analytics.dto.DashboardAnalyticsResponse.Snapshot;
import com.nexploy.campuslink.analytics.dto.InstituteAnalyticsResponse;
import com.nexploy.campuslink.analytics.dto.InstituteAnalyticsResponse.CampusComparison;
import com.nexploy.campuslink.analytics.dto.PlacementSummaryResponse;
import com.nexploy.campuslink.analytics.dto.SkillAnalyticsResponse;
import com.nexploy.campuslink.analytics.dto.SkillAnalyticsResponse.SkillDemand;
import com.nexploy.campuslink.analytics.dto.SkillAnalyticsResponse.SkillSupply;
import com.nexploy.campuslink.application.entity.Application;
import com.nexploy.campuslink.application.entity.ApplicationStatus;
import com.nexploy.campuslink.application.repository.ApplicationRepository;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.campus.entity.Campus;
import com.nexploy.campuslink.campus.repository.CampusRepository;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.offer.entity.Offer;
import com.nexploy.campuslink.offer.entity.OfferStatus;
import com.nexploy.campuslink.offer.repository.OfferRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentSkill;
import com.nexploy.campuslink.student.repository.StudentRepository;
import com.nexploy.campuslink.student.repository.StudentSkillRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only, campus-scoped placement analytics. Aggregations only: no student identity leaves
 * this layer and no result drives a decision. Access is enforced per campus before any read.
 * "Placed" is defined deterministically as having an accepted offer rather than the free-text
 * placement status, so the placement rate does not depend on inconsistent status strings.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final StudentRepository studentRepository;
    private final DriveRepository driveRepository;
    private final OfferRepository offerRepository;
    private final ApplicationRepository applicationRepository;
    private final CampusAccessService campusAccessService;
    private final CampusRepository campusRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Skill supply/demand for one campus: how many active drives require each skill, paired with the
     * share of the campus cohort that claims it. Access is enforced before any read; an empty campus
     * yields empty lists rather than placeholder skills.
     */
    @Transactional(readOnly = true)
    public SkillAnalyticsResponse skillAnalytics(String campusId) {
        UserAccount user = campusAccessService.currentUser();
        campusAccessService.requireAccess(user, campusId);

        List<Drive> activeDrives = driveRepository.findByCampusId(campusId).stream()
            .filter(d -> d.getStatus() == DriveStatus.ACTIVE).toList();
        List<Student> students = studentRepository.findByCampusId(campusId);
        List<StudentSkill> cohortSkills = studentSkillRepository.findByCampusId(campusId);

        // Demand: count active drives requiring each skill. Key by lower-case for a stable canonical
        // grouping; keep the first-seen label for display.
        Map<String, Integer> demandCounts = new LinkedHashMap<>();
        Map<String, String> displayLabel = new LinkedHashMap<>();
        for (Drive drive : activeDrives) {
            for (String skill : parseSkills(drive.getRequiredSkillsJson())) {
                String key = skill.toLowerCase(Locale.ROOT);
                demandCounts.merge(key, 1, Integer::sum);
                displayLabel.putIfAbsent(key, skill);
            }
        }

        // Supply: distinct students per skill (case-insensitive), turned into cohort coverage %.
        Map<String, Set<String>> holdersByskill = new LinkedHashMap<>();
        for (StudentSkill s : cohortSkills) {
            if (s.getSkill() == null) continue;
            holdersByskill
                .computeIfAbsent(s.getSkill().toLowerCase(Locale.ROOT), k -> new java.util.HashSet<>())
                .add(s.getStudentId());
        }
        long cohortSize = students.size();

        List<SkillDemand> demand = new ArrayList<>();
        List<SkillSupply> supply = new ArrayList<>();
        // Report demand-side skills first (what employers ask for), highest demand first.
        demandCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(e -> {
                String label = displayLabel.get(e.getKey());
                int demandScore = e.getValue();
                int holders = holdersByskill.getOrDefault(e.getKey(), Set.of()).size();
                demand.add(new SkillDemand(label, demandScore));
                supply.add(new SkillSupply(label, demandScore, (int) Math.round(pct(holders, cohortSize))));
            });

        return new SkillAnalyticsResponse(demand, supply);
    }

    private List<String> parseSkills(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> skills = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return skills.stream().filter(s -> s != null && !s.isBlank()).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    @Transactional(readOnly = true)
    public InstituteAnalyticsResponse institute() {
        UserAccount user = campusAccessService.currentUser();

        List<Campus> campuses;
        // Only admins get the cross-campus comparison; officers see just their own campus.
        if (user.getRole() == UserRole.ADMIN) {
            campuses = campusRepository.findAll();
        } else {
            campuses = campusRepository.findById(user.getCampusId())
                .map(List::of).orElse(List.of());
        }

        List<CampusComparison> rows = new ArrayList<>();
        long totalStudents = 0, totalReady = 0, totalPlaced = 0, totalAtRisk = 0;
        long totalOffers = 0, totalActiveDrives = 0;
        Set<String> recruiterIds = new java.util.HashSet<>();

        for (Campus campus : campuses) {
            List<Student> students = studentRepository.findByCampusId(campus.getId());
            List<Drive> drives = driveRepository.findByCampusId(campus.getId());
            List<Offer> offers = offerRepository.findByCampusId(campus.getId());
            Set<String> studentIds = students.stream().map(Student::getId).collect(Collectors.toSet());

            long students0 = students.size();
            long ready = students.stream()
                .filter(s -> s.getReadinessScore() != null && s.getReadinessScore() >= READY_THRESHOLD).count();
            long placed = offers.stream()
                .filter(o -> o.getStatus() == OfferStatus.ACCEPTED)
                .map(Offer::getStudentId).filter(studentIds::contains).distinct().count();
            long atRisk = students.stream().filter(s -> "HIGH".equalsIgnoreCase(s.getRiskLevel())).count();
            long activeDrives = drives.stream().filter(d -> d.getStatus() == DriveStatus.ACTIVE).count();

            rows.add(new CampusComparison(
                campus.getId(),
                campus.getName(),
                campus.getCity(),
                students0,
                ready,
                placed,
                (int) Math.round(pct(placed, students0)),
                formatLpa(offers.stream().map(Offer::getPackageLpa).filter(p -> p != null)
                    .mapToDouble(BigDecimal::doubleValue).average().orElse(0.0))));

            totalStudents += students0;
            totalReady += ready;
            totalPlaced += placed;
            totalAtRisk += atRisk;
            totalOffers += offers.size();
            totalActiveDrives += activeDrives;
            drives.stream().map(Drive::getRecruiterId).filter(id -> id != null).forEach(recruiterIds::add);
        }

        InstituteAnalyticsResponse.Totals totals = new InstituteAnalyticsResponse.Totals(
            totalStudents, totalReady, totalPlaced, totalAtRisk,
            totalOffers, totalActiveDrives, recruiterIds.size(),
            (int) Math.round(pct(totalPlaced, totalStudents)));

        return new InstituteAnalyticsResponse(totals, rows);
    }

    @Transactional(readOnly = true)
    public PlacementSummaryResponse placementSummary(String campusId) {
        UserAccount user = campusAccessService.currentUser();
        campusAccessService.requireAccess(user, campusId);

        // Fully set-based: the summary is aggregate counts only, so it loads no entity rows —
        // each figure is a COUNT/AVG/GROUP BY pushed to the database (portable JPQL).
        long studentCount = studentRepository.countByCampusId(campusId);
        long placedCount = offerRepository.countPlacedStudentsByCampusId(campusId);

        Map<String, Long> offersByStatus = foldEnumCounts(offerRepository.countByStatusForCampus(campusId));
        long accepted = offersByStatus.getOrDefault("ACCEPTED", 0L);
        long declined = offersByStatus.getOrDefault("DECLINED", 0L);

        return new PlacementSummaryResponse(
            campusId,
            studentCount,
            placedCount,
            ratio(placedCount, studentCount),
            studentRepository.averageReadinessByCampusId(campusId),
            foldStringCounts(studentRepository.countByPlacementStatusForCampus(campusId)),
            driveRepository.countByCampusId(campusId),
            foldEnumCounts(driveRepository.countByStatusForCampus(campusId)),
            offerRepository.countByCampusId(campusId),
            offersByStatus,
            ratio(accepted, accepted + declined),
            applicationRepository.countByCampusId(campusId),
            foldEnumCounts(applicationRepository.countByStatusForCampus(campusId))
        );
    }

    /** Folds a [enum, count] aggregate result into a name-keyed map (a null key becomes UNKNOWN). */
    private Map<String, Long> foldEnumCounts(List<Object[]> rows) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String key = row[0] == null ? "UNKNOWN" : ((Enum<?>) row[0]).name();
            out.merge(key, ((Number) row[1]).longValue(), Long::sum);
        }
        return out;
    }

    /** Folds a [string, count] aggregate result into a map (a null key becomes UNKNOWN). */
    private Map<String, Long> foldStringCounts(List<Object[]> rows) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String key = row[0] == null ? "UNKNOWN" : row[0].toString();
            out.merge(key, ((Number) row[1]).longValue(), Long::sum);
        }
        return out;
    }

    /** Readiness at or above this counts as "placement ready" for the dashboard rollups. */
    private static final int READY_THRESHOLD = 70;

    /**
     * The dashboard analytics bundle for a campus, shaped to the frontend CampusAnalytics.
     * All series are aggregations over this campus's students/drives/offers/applications.
     */
    @Transactional(readOnly = true)
    public DashboardAnalyticsResponse dashboard(String campusId) {
        UserAccount user = campusAccessService.currentUser();
        campusAccessService.requireAccess(user, campusId);

        List<Student> students = studentRepository.findByCampusId(campusId);
        List<Drive> drives = driveRepository.findByCampusId(campusId);
        List<Offer> offers = offerRepository.findByCampusId(campusId);
        Set<String> studentIds = students.stream().map(Student::getId).collect(Collectors.toSet());
        // Single campus-scoped join instead of one query per student (was an N+1 over findByStudentId).
        List<Application> applications = applicationRepository.findByCampusId(campusId);

        long total = students.size();
        Set<String> placedIds = offers.stream()
            .filter(o -> o.getStatus() == OfferStatus.ACCEPTED)
            .map(Offer::getStudentId).filter(studentIds::contains)
            .collect(Collectors.toSet());
        long placed = placedIds.size();
        long ready = students.stream()
            .filter(s -> s.getReadinessScore() != null && s.getReadinessScore() >= READY_THRESHOLD).count();
        long atRisk = students.stream().filter(s -> "HIGH".equalsIgnoreCase(s.getRiskLevel())).count();

        Snapshot snapshot = new Snapshot(
            total,
            ready,
            atRisk,
            drives.stream().filter(d -> d.getStatus() == DriveStatus.ACTIVE).count(),
            applications.size(),
            offers.size(),
            placed,
            (int) Math.round(pct(placed, total)),
            drives.stream().map(Drive::getRecruiterId).filter(id -> id != null).distinct().count(),
            formatLpa(offers.stream().map(Offer::getPackageLpa).filter(p -> p != null)
                .mapToDouble(BigDecimal::doubleValue).average().orElse(0.0)),
            formatLpa(offers.stream().map(Offer::getPackageLpa).filter(p -> p != null)
                .mapToDouble(BigDecimal::doubleValue).max().orElse(0.0)));

        return new DashboardAnalyticsResponse(
            snapshot,
            branchStats(students, placedIds),
            funnel(students, applications, offers, ready, placed),
            offerPipeline(offers),
            salaryTrend(offers),
            readinessDistribution(students),
            riskDistribution(students),
            recruiterEngagement(drives, offers),
            alerts(atRisk, applications, students));
    }

    private List<BranchStat> branchStats(List<Student> students, Set<String> placedIds) {
        Map<String, List<Student>> byBranch = students.stream()
            .collect(Collectors.groupingBy(
                s -> s.getBranchCode() == null ? "UNKNOWN" : s.getBranchCode(),
                TreeMap::new, Collectors.toList()));
        List<BranchStat> stats = new ArrayList<>();
        byBranch.forEach((code, group) -> {
            long placed = group.stream().filter(s -> placedIds.contains(s.getId())).count();
            String branch = group.get(0).getBranch() == null ? code : group.get(0).getBranch();
            stats.add(new BranchStat(branch, code, (int) Math.round(pct(placed, group.size())), placed, group.size()));
        });
        return stats;
    }

    private List<FunnelStage> funnel(List<Student> students, List<Application> applications,
                                     List<Offer> offers, long ready, long placed) {
        Set<String> applied = studentsWith(applications, EnumSet.of(
            ApplicationStatus.APPLIED, ApplicationStatus.SHORTLISTED, ApplicationStatus.ASSESSMENT,
            ApplicationStatus.INTERVIEW, ApplicationStatus.SELECTED, ApplicationStatus.OFFER, ApplicationStatus.JOINED));
        Set<String> shortlisted = studentsWith(applications, EnumSet.of(
            ApplicationStatus.SHORTLISTED, ApplicationStatus.ASSESSMENT, ApplicationStatus.INTERVIEW,
            ApplicationStatus.SELECTED, ApplicationStatus.OFFER, ApplicationStatus.JOINED));
        Set<String> interviewed = studentsWith(applications, EnumSet.of(
            ApplicationStatus.INTERVIEW, ApplicationStatus.SELECTED, ApplicationStatus.OFFER, ApplicationStatus.JOINED));
        long offered = offers.stream().map(Offer::getStudentId).distinct().count();
        return List.of(
            new FunnelStage("Registered", students.size()),
            new FunnelStage("Placement Ready", ready),
            new FunnelStage("Applied", applied.size()),
            new FunnelStage("Shortlisted", shortlisted.size()),
            new FunnelStage("Interviewed", interviewed.size()),
            new FunnelStage("Offered", offered),
            new FunnelStage("Joined", placed));
    }

    private Set<String> studentsWith(List<Application> applications, Set<ApplicationStatus> statuses) {
        return applications.stream()
            .filter(a -> statuses.contains(a.getStatus()))
            .map(Application::getStudentId)
            .collect(Collectors.toSet());
    }

    private List<MonthCount> offerPipeline(List<Offer> offers) {
        Map<Integer, Long> byMonth = offers.stream()
            .filter(o -> o.getExtendedAt() != null)
            .collect(Collectors.groupingBy(o -> o.getExtendedAt().getMonthValue(),
                TreeMap::new, Collectors.counting()));
        List<MonthCount> out = new ArrayList<>();
        byMonth.forEach((m, count) -> out.add(new MonthCount(monthName(m), count)));
        return out;
    }

    private List<SalaryPoint> salaryTrend(List<Offer> offers) {
        Map<Integer, List<Offer>> byMonth = offers.stream()
            .filter(o -> o.getExtendedAt() != null && o.getPackageLpa() != null)
            .collect(Collectors.groupingBy(o -> o.getExtendedAt().getMonthValue(), TreeMap::new, Collectors.toList()));
        List<SalaryPoint> out = new ArrayList<>();
        byMonth.forEach((m, group) -> {
            double avg = group.stream().mapToDouble(o -> o.getPackageLpa().doubleValue()).average().orElse(0.0);
            double max = group.stream().mapToDouble(o -> o.getPackageLpa().doubleValue()).max().orElse(0.0);
            out.add(new SalaryPoint(monthName(m), round1(avg), round1(max)));
        });
        return out;
    }

    private List<BandCount> readinessDistribution(List<Student> students) {
        long b1 = 0, b2 = 0, b3 = 0, b4 = 0;
        for (Student s : students) {
            Integer r = s.getReadinessScore();
            if (r == null) continue;
            if (r <= 40) b1++;
            else if (r <= 60) b2++;
            else if (r <= 80) b3++;
            else b4++;
        }
        return List.of(
            new BandCount("0-40", b1), new BandCount("41-60", b2),
            new BandCount("61-80", b3), new BandCount("81-100", b4));
    }

    private List<LevelCount> riskDistribution(List<Student> students) {
        Map<String, Long> byLevel = students.stream()
            .map(s -> s.getRiskLevel() == null ? "LOW" : s.getRiskLevel().toUpperCase(Locale.ROOT))
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return List.of(
            new LevelCount("LOW", byLevel.getOrDefault("LOW", 0L)),
            new LevelCount("MEDIUM", byLevel.getOrDefault("MEDIUM", 0L)),
            new LevelCount("HIGH", byLevel.getOrDefault("HIGH", 0L)));
    }

    private List<RecruiterEngagement> recruiterEngagement(List<Drive> drives, List<Offer> offers) {
        Map<String, long[]> byCompany = new TreeMap<>();
        for (Drive d : drives) {
            String c = d.getCompanyName() == null ? "Unknown" : d.getCompanyName();
            byCompany.computeIfAbsent(c, k -> new long[2])[0]++;
        }
        for (Offer o : offers) {
            String c = o.getCompanyName() == null ? "Unknown" : o.getCompanyName();
            byCompany.computeIfAbsent(c, k -> new long[2])[1]++;
        }
        List<RecruiterEngagement> out = new ArrayList<>();
        byCompany.forEach((c, counts) -> out.add(new RecruiterEngagement(c, counts[0], counts[1])));
        out.sort(Comparator.comparingLong((RecruiterEngagement r) -> r.offers()).reversed());
        return out;
    }

    private List<Alert> alerts(long atRisk, List<Application> applications, List<Student> students) {
        List<Alert> alerts = new ArrayList<>();
        if (atRisk > 0) {
            alerts.add(new Alert("alert_risk", "HIGH", atRisk + " students flagged HIGH risk and need intervention."));
        }
        long noApplications = students.stream()
            .filter(s -> applications.stream().noneMatch(a -> a.getStudentId().equals(s.getId())))
            .count();
        if (noApplications > 0) {
            alerts.add(new Alert("alert_inactive", "MEDIUM", noApplications + " students have not applied to any drive."));
        }
        return alerts;
    }

    private String monthName(int month) {
        return java.time.Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    private String formatLpa(double value) {
        return "₹" + round1(value) + " LPA";
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double pct(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator * 100.0;
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : Math.round((double) numerator / denominator * 10000.0) / 10000.0;
    }
}
