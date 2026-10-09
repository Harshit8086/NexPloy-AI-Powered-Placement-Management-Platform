-- ============================================================================
-- V38 — Canonical demo dataset v2 (Phase 1 re-seed).
--
-- WHY THIS EXISTS: V23 seeded the canonical dataset, but V23 runs *before* V29,
-- which wipes every business table. On a fresh `flyway migrate` the sequence is
-- V23 (seed) -> V29 (delete) -> ... -> V38, so after V29 the DB holds only the
-- bootstrap campus (cmp_main) + officer login (Officer123@gmail.com). This
-- migration re-establishes the same demo data AND populates the Phase 1 tables
-- introduced in V34-V36 (placement_cycles, match_results, shortlist_decisions,
-- application_status_history) plus readiness_snapshots / risk_entries /
-- notifications, so the officer dashboard, AI Match Review and funnel views all
-- show real backend rows.
--
-- DEMO DATA: this is a demo/prototype seed of REAL backend rows (not frontend
-- mocks). For a production deployment, relocate the seed migrations to a
-- Flyway location activated only under a demo/dev profile.
--
-- IDEMPOTENT: every INSERT is ON CONFLICT (id) DO UPDATE. FK-ordered:
-- campuses -> recruiters -> students -> placement_cycles -> drives ->
-- applications -> application_status_history -> match_results ->
-- shortlist_decisions -> interviews -> offers -> readiness_snapshots ->
-- risk_entries -> notifications -> documents.
-- All FKs added in V33-V36 are respected (campus_id, student_id, drive_id,
-- recruiter_id, cycle_id, application_id, match_result_id, recipient_username).
-- ============================================================================

-- Scope the bootstrap officer to the campus the frontend defaults to.
UPDATE user_accounts SET campus_id = 'cmp_main' WHERE username = 'Officer123@gmail.com';

-- ---------- campuses ----------
INSERT INTO campuses (id, name, city, state, active) VALUES
  ('cmp_main', 'Main Campus',        'Bengaluru', 'Karnataka',   true),
  ('cmp_city', 'City Campus',        'Pune',      'Maharashtra', true),
  ('cmp_engg', 'Engineering Campus', 'Hyderabad', 'Telangana',   true)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name, city = EXCLUDED.city, state = EXCLUDED.state, active = EXCLUDED.active;

-- ---------- recruiters ----------
INSERT INTO recruiters (id, campus_id, name, company_name, email, active, company_website, company_industry, company_location) VALUES
  ('rec_technova',    'cmp_main', 'TechNova Talent',    'TechNova',    'talent@technova.demo',    true, 'technova.demo',    'Fintech / Enterprise Software', 'Bengaluru, India'),
  ('rec_cloudsphere', 'cmp_main', 'CloudSphere Talent', 'CloudSphere', 'talent@cloudsphere.demo', true, 'cloudsphere.demo', 'Cloud Infrastructure',          'Hyderabad, India'),
  ('rec_datavision',  'cmp_main', 'DataVision Talent',  'DataVision',  'talent@datavision.demo',  true, 'datavision.demo',  'Data & Analytics',              'Pune, India'),
  ('rec_innosoft',    'cmp_main', 'InnoSoft Talent',    'InnoSoft',    'talent@innosoft.demo',    true, 'innosoft.demo',    'Enterprise SaaS',               'Chennai, India'),
  ('rec_quantflow',   'cmp_main', 'QuantFlow Talent',   'QuantFlow',   'talent@quantflow.demo',   true, 'quantflow.demo',   'Quantitative Trading',          'Bengaluru, India'),
  ('rec_medisys',     'cmp_main', 'MediSys Talent',     'MediSys',     'talent@medisys.demo',     true, 'medisys.demo',     'Medical Devices',               'Bengaluru, India'),
  ('rec_aerobuild',   'cmp_main', 'AeroBuild Talent',   'AeroBuild',   'talent@aerobuild.demo',   true, 'aerobuild.demo',   'Aerospace Manufacturing',       'Pune, India'),
  ('rec_finedge',     'cmp_main', 'FinEdge Talent',     'FinEdge',     'talent@finedge.demo',     true, 'finedge.demo',     'Financial Services',            'Mumbai, India'),
  ('rec_brightlearn', 'cmp_main', 'BrightLearn Talent', 'BrightLearn', 'talent@brightlearn.demo', true, 'brightlearn.demo', 'EdTech',                        'Hyderabad, India')
ON CONFLICT (id) DO UPDATE SET
  campus_id = EXCLUDED.campus_id, name = EXCLUDED.name, company_name = EXCLUDED.company_name,
  email = EXCLUDED.email, active = EXCLUDED.active, company_website = EXCLUDED.company_website,
  company_industry = EXCLUDED.company_industry, company_location = EXCLUDED.company_location;

-- ---------- students ----------
INSERT INTO students (
  id, campus_id, name, email, phone, roll_number, branch, branch_code, graduation_year,
  cgpa, backlogs, readiness_score, placement_status, tenth_percentage, twelfth_percentage,
  department, batch, semester, year_label, backlog_history, resume_score, placement_probability,
  target_role, risk_level, risk_reason, strengths_json, critical_gaps_json, certifications_json,
  projects_json, assessments_json, breakdown_json, readiness_trend_json, preferred_roles_json,
  preferred_domains_json, preferred_locations_json
) VALUES
  ('stu_rahul','cmp_main','Rahul Sharma','rahul.sharma@nexploy.demo','+91 98450 21478','NXP2023CSE0147','Computer Science','CSE',2027,8.59,0,78,'IN PROCESS',92.4,89.6,'School of Engineering','2023 – 2027',7,'Final Year',0,76,68,'Backend Developer','LOW','On Track','["Java","Spring Boot","SQL","REST APIs","Problem Solving"]','["Docker","AWS","Communication"]','["Oracle Certified Java Associate","MongoDB Developer Essentials"]','[{"name":"Campus Payments API","stack":["Java","Spring Boot","PostgreSQL"],"summary":"Transactional payments service with idempotent ledger writes and role-based access."},{"name":"Library Microservice","stack":["Java","Spring Cloud","MySQL"],"summary":"Service-to-service catalogue system with circuit breakers and centralized config."},{"name":"Realtime Attendance Tracker","stack":["Node.js","WebSocket","Redis"],"summary":"Low-latency attendance capture used by 400+ students during a pilot semester."}]','{"aptitude":79,"coding":74,"technical":82,"communication":69,"mockInterview":72}','{"academic":86,"technical":82,"coding":74,"aptitude":79,"communication":69,"projects":88,"interview":72,"placementActivity":76}','[58,62,68,74,78]','["Backend Developer","Cloud Engineer","Platform Engineer"]','["Fintech","Cloud Infrastructure","Enterprise SaaS"]','["Bengaluru","Hyderabad","Pune"]'),
  ('stu_priya','cmp_main','Priya Das','priya.das@nexploy.demo','+91 98450 33210','NXP2023CSE0102','Computer Science','CSE',2027,8.92,0,85,'IN PROCESS',94.1,91.8,'School of Engineering','2023 – 2027',7,'Final Year',0,84,74,'Backend Developer','LOW','On Track','["Python","Django","System Design","Communication"]','["Kubernetes"]','["AWS Cloud Practitioner"]','[{"name":"Distributed Job Scheduler","stack":["Python","Celery","Redis"],"summary":"Fault-tolerant scheduler handling retries, backoff and dead-letter queues."},{"name":"Analytics Warehouse","stack":["Python","Airflow","PostgreSQL"],"summary":"Nightly ETL pipeline consolidating six source systems into a star schema."}]','{"aptitude":88,"coding":85,"technical":88,"communication":83,"mockInterview":82}','{"academic":92,"technical":88,"coding":85,"aptitude":88,"communication":83,"projects":86,"interview":80,"placementActivity":78}','[60,66,72,79,85]','["Backend Developer","Platform Engineer"]','["Cloud Infrastructure","Data Platforms"]','["Bengaluru","Hyderabad"]'),
  ('stu_arjun','cmp_main','Arjun Patel','arjun.patel@nexploy.demo','+91 98450 55112','NXP2023IT0088','Information Technology','IT',2027,8.21,0,81,'IN PROCESS',90.2,87.4,'School of Engineering','2023 – 2027',7,'Final Year',0,78,70,'Backend Developer','LOW','On Track','["Java","Spring Boot","Kafka","SQL"]','["System Design","AWS"]','["Confluent Kafka Fundamentals"]','[{"name":"Event Streaming Gateway","stack":["Java","Kafka","Spring Boot"],"summary":"Ingestion gateway processing 20k simulated events/minute with exactly-once semantics."}]','{"aptitude":80,"coding":79,"technical":83,"communication":76,"mockInterview":75}','{"academic":84,"technical":83,"coding":79,"aptitude":80,"communication":76,"projects":82,"interview":75,"placementActivity":79}','[59,64,70,76,81]','["Backend Developer","Data Engineer"]','["Fintech","Enterprise SaaS"]','["Bengaluru","Pune"]'),
  ('stu_ankit','cmp_main','Ankit Rao','ankit.rao@nexploy.demo','+91 98450 77431','NXP2023IT0034','Information Technology','IT',2027,7.84,0,79,'IN PROCESS',86.5,83.2,'School of Engineering','2023 – 2027',7,'Final Year',0,72,65,'Backend Developer','LOW','On Track','["Java","Microservices","SQL"]','["AWS"]','[]','[{"name":"Inventory Microservices","stack":["Java","Spring Boot","MySQL"],"summary":"Four-service inventory system with API gateway and centralized auth."}]','{"aptitude":76,"coding":75,"technical":80,"communication":78,"mockInterview":74}','{"academic":76,"technical":80,"coding":75,"aptitude":76,"communication":78,"projects":81,"interview":74,"placementActivity":80}','[55,60,66,73,79]','["Backend Developer"]','["Enterprise SaaS"]','["Bengaluru","Chennai"]'),
  ('stu_neha','cmp_main','Neha Singh','neha.singh@nexploy.demo','+91 98450 66120','NXP2023CSE0211','Computer Science','CSE',2027,8.12,0,76,'IN PROCESS',88.9,85.1,'School of Engineering','2023 – 2027',7,'Final Year',0,70,62,'Backend Developer','MEDIUM','Interview conversion below benchmark','["Java","Spring Boot","REST APIs"]','["Docker","System Design"]','[]','[{"name":"Hostel Management Portal","stack":["Java","Spring Boot","React"],"summary":"Room allocation and complaint tracking portal with audit trails."}]','{"aptitude":74,"coding":72,"technical":78,"communication":74,"mockInterview":66}','{"academic":82,"technical":78,"coding":72,"aptitude":74,"communication":74,"projects":77,"interview":69,"placementActivity":72}','[54,59,65,71,76]','["Backend Developer","Full Stack Developer"]','["Enterprise SaaS"]','["Bengaluru"]'),
  ('stu_kavya','cmp_main','Kavya Menon','kavya.menon@nexploy.demo','+91 98450 11009','NXP2023CSE0009','Computer Science','CSE',2027,9.12,0,88,'OFFERED',96.0,94.2,'School of Engineering','2023 – 2027',7,'Final Year',0,89,79,'Software Engineer','LOW','On Track','["C++","Algorithms","System Design","Go"]','[]','["Google Cloud Associate Engineer"]','[{"name":"In-Memory Key-Value Store","stack":["Go","gRPC"],"summary":"Raft-backed KV store with snapshotting and leader election."}]','{"aptitude":92,"coding":94,"technical":90,"communication":84,"mockInterview":86}','{"academic":94,"technical":90,"coding":94,"aptitude":92,"communication":84,"projects":87,"interview":86,"placementActivity":77}','[68,73,79,84,88]','["Software Engineer","Systems Engineer"]','["Product Engineering"]','["Bengaluru"]'),
  ('stu_sneha','cmp_main','Sneha Das','sneha.das@nexploy.demo','+91 98450 44118','NXP2023ECE0156','Electronics & Communication','ECE',2027,7.15,0,51,'IN PROCESS',81.4,78.6,'School of Engineering','2023 – 2027',7,'Final Year',0,58,44,'Embedded Systems Engineer','HIGH','Repeated interview failures','["Embedded Systems","C Programming"]','["Interview Performance","Communication"]','[]','[{"name":"Smart Energy Meter","stack":["C","ESP32","MQTT"],"summary":"IoT energy meter streaming consumption telemetry to a local dashboard."}]','{"aptitude":60,"coding":52,"technical":55,"communication":50,"mockInterview":28}','{"academic":68,"technical":55,"coding":52,"aptitude":60,"communication":50,"projects":54,"interview":28,"placementActivity":41}','[50,52,49,51,51]','["Embedded Systems Engineer"]','["Hardware","IoT"]','["Bengaluru","Pune"]'),
  ('stu_aman','cmp_main','Aman Kumar','aman.kumar@nexploy.demo','+91 98450 90312','NXP2023CSE0318','Computer Science','CSE',2027,6.95,3,46,'UNPLACED',76.2,72.8,'School of Engineering','2023 – 2027',7,'Final Year',5,41,29,'Software Engineer','HIGH','No applications in 30 days','["HTML/CSS","Basic Java"]','["DSA","Communication","Placement Activity"]','[]','[{"name":"Personal Portfolio","stack":["HTML","CSS","JavaScript"],"summary":"Static personal site; no backend or deployment automation yet."}]','{"aptitude":48,"coding":41,"technical":46,"communication":54,"mockInterview":38}','{"academic":58,"technical":46,"coding":41,"aptitude":48,"communication":54,"projects":44,"interview":38,"placementActivity":22}','[52,50,48,47,46]','["Software Engineer"]','["Product Engineering"]','["Bengaluru"]'),
  ('stu_amit','cmp_main','Amit Kumar','amit.kumar@nexploy.demo','+91 98450 22087','NXP2023MEC0072','Mechanical Engineering','MECH',2027,6.42,1,43,'UNPLACED',74.8,71.2,'School of Engineering','2023 – 2027',7,'Final Year',2,44,31,'Core Engineering','HIGH','No recent applications','["CAD","Manufacturing Basics"]','["Aptitude","Communication","Domain Projects"]','[]','[]','{"aptitude":42,"coding":30,"technical":38,"communication":45,"mockInterview":30}','{"academic":52,"technical":38,"coding":30,"aptitude":42,"communication":45,"projects":35,"interview":30,"placementActivity":26}','[48,46,44,43,43]','["Design Engineer"]','["Core Engineering"]','["Pune","Chennai"]'),
  ('stu_rohit','cmp_city','Rohit Verma','rohit.verma@nexploy.demo','+91 98450 30021','NXC2023IT0021','Information Technology','IT',2027,7.42,1,64,'IN PROCESS',82.0,79.5,'School of Engineering','2023 – 2027',7,'Final Year',1,62,52,'Frontend Developer','MEDIUM','Active backlog limits drive eligibility','["JavaScript","React"]','["Backend Depth","DSA"]','[]','[]','{"aptitude":66,"coding":58,"technical":64,"communication":70,"mockInterview":60}','{"academic":72,"technical":64,"coding":58,"aptitude":66,"communication":70,"projects":62,"interview":60,"placementActivity":58}','[55,58,60,62,64]','["Frontend Developer"]','["Product Engineering"]','["Pune"]'),
  ('stu_isha','cmp_city','Isha Kulkarni','isha.kulkarni@nexploy.demo','+91 98450 30044','NXC2023ECE0044','Electronics & Communication','ECE',2027,8.05,0,73,'IN PROCESS',89.1,86.7,'School of Engineering','2023 – 2027',7,'Final Year',0,74,61,'Data Analyst','LOW','On Track','["Python","Signal Processing","Data Visualization"]','["SQL Depth"]','[]','[]','{"aptitude":78,"coding":70,"technical":74,"communication":80,"mockInterview":72}','{"academic":82,"technical":74,"coding":70,"aptitude":78,"communication":80,"projects":71,"interview":72,"placementActivity":66}','[58,62,66,70,73]','["Data Analyst"]','["Data & Analytics"]','["Pune","Bengaluru"]'),
  ('stu_vikram','cmp_city','Vikram Singh','vikram.singh@nexploy.demo','+91 98450 30067','NXC2023EEE0067','Electrical Engineering','EEE',2027,6.88,2,48,'UNPLACED',75.6,73.4,'School of Engineering','2023 – 2027',7,'Final Year',4,46,33,'Core Engineering','HIGH','Readiness declining with 2 active backlogs','["Power Systems","MATLAB"]','["Aptitude","Programming","Placement Activity"]','[]','[]','{"aptitude":45,"coding":34,"technical":48,"communication":52,"mockInterview":36}','{"academic":56,"technical":48,"coding":34,"aptitude":45,"communication":52,"projects":46,"interview":36,"placementActivity":28}','[54,52,50,49,48]','["Electrical Design Engineer"]','["Core Engineering"]','["Pune"]'),
  ('stu_meera','cmp_engg','Meera Iyer','meera.iyer@nexploy.demo','+91 98450 40012','NXE2023CSE0012','Computer Science','CSE',2027,8.36,0,80,'IN PROCESS',91.0,88.3,'School of Engineering','2023 – 2027',7,'Final Year',0,79,69,'Data Analyst','LOW','On Track','["Python","Machine Learning","SQL"]','["System Design"]','[]','[]','{"aptitude":84,"coding":78,"technical":80,"communication":79,"mockInterview":76}','{"academic":86,"technical":80,"coding":78,"aptitude":84,"communication":79,"projects":81,"interview":76,"placementActivity":74}','[62,67,72,76,80]','["Data Analyst","ML Engineer"]','["Data & Analytics"]','["Hyderabad","Bengaluru"]'),
  ('stu_farhan','cmp_engg','Farhan Qureshi','farhan.qureshi@nexploy.demo','+91 98450 40055','NXE2023MEC0055','Mechanical Engineering','MECH',2027,7.05,0,58,'UNPLACED',80.4,77.9,'School of Engineering','2023 – 2027',7,'Final Year',0,55,41,'Core Engineering','MEDIUM','Low aptitude score for core drives','["SolidWorks","Thermodynamics"]','["Aptitude","Projects"]','[]','[]','{"aptitude":58,"coding":40,"technical":60,"communication":64,"mockInterview":52}','{"academic":70,"technical":60,"coding":40,"aptitude":58,"communication":64,"projects":52,"interview":52,"placementActivity":48}','[50,52,55,57,58]','["Design Engineer"]','["Core Engineering"]','["Hyderabad","Chennai"]')
ON CONFLICT (id) DO UPDATE SET
  campus_id = EXCLUDED.campus_id, name = EXCLUDED.name, email = EXCLUDED.email, phone = EXCLUDED.phone,
  roll_number = EXCLUDED.roll_number, branch = EXCLUDED.branch, branch_code = EXCLUDED.branch_code,
  graduation_year = EXCLUDED.graduation_year, cgpa = EXCLUDED.cgpa, backlogs = EXCLUDED.backlogs,
  readiness_score = EXCLUDED.readiness_score, placement_status = EXCLUDED.placement_status,
  tenth_percentage = EXCLUDED.tenth_percentage, twelfth_percentage = EXCLUDED.twelfth_percentage,
  department = EXCLUDED.department, batch = EXCLUDED.batch, semester = EXCLUDED.semester,
  year_label = EXCLUDED.year_label, backlog_history = EXCLUDED.backlog_history,
  resume_score = EXCLUDED.resume_score, placement_probability = EXCLUDED.placement_probability,
  target_role = EXCLUDED.target_role, risk_level = EXCLUDED.risk_level, risk_reason = EXCLUDED.risk_reason,
  strengths_json = EXCLUDED.strengths_json, critical_gaps_json = EXCLUDED.critical_gaps_json,
  certifications_json = EXCLUDED.certifications_json, projects_json = EXCLUDED.projects_json,
  assessments_json = EXCLUDED.assessments_json, breakdown_json = EXCLUDED.breakdown_json,
  readiness_trend_json = EXCLUDED.readiness_trend_json, preferred_roles_json = EXCLUDED.preferred_roles_json,
  preferred_domains_json = EXCLUDED.preferred_domains_json, preferred_locations_json = EXCLUDED.preferred_locations_json;

-- ---------- placement_cycles (one active cycle per campus; V34) ----------
INSERT INTO placement_cycles (id, campus_id, name, academic_year, starts_on, ends_on, status) VALUES
  ('pc_main_2027', 'cmp_main', 'Placement Season 2026-27', '2026-2027', DATE '2026-06-01', DATE '2027-05-31', 'ACTIVE'),
  ('pc_city_2027', 'cmp_city', 'Placement Season 2026-27', '2026-2027', DATE '2026-06-01', DATE '2027-05-31', 'ACTIVE'),
  ('pc_engg_2027', 'cmp_engg', 'Placement Season 2026-27', '2026-2027', DATE '2026-06-01', DATE '2027-05-31', 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET
  campus_id = EXCLUDED.campus_id, name = EXCLUDED.name, academic_year = EXCLUDED.academic_year,
  starts_on = EXCLUDED.starts_on, ends_on = EXCLUDED.ends_on, status = EXCLUDED.status;

-- ---------- drives ----------
INSERT INTO drives (
  id, campus_id, cycle_id, recruiter_id, company_name, role, location, package_lpa, minimum_cgpa,
  maximum_backlogs, graduation_year, deadline, status, drive_type, description, expected_hiring,
  venue, minimum_tenth_percentage, minimum_twelfth_percentage, required_skills_json, rounds_json,
  allowed_branches_json
) VALUES
  ('drv_technova','cmp_main','pc_main_2027','rec_technova','TechNova','Backend Developer','Bengaluru',8.50,7.0,1,2027,DATE '2026-09-18','ACTIVE','On-Campus','TechNova is hiring Backend Developers to build high-scale distributed services powering its fintech platform. You''ll work with Java, Spring Boot, and cloud-native infrastructure.',20,'Lab 2',70,NULL,'["Java","Spring Boot","SQL","REST APIs","Docker","AWS"]','[{"name":"Online Assessment","description":"DSA + Java fundamentals, 90 minutes."},{"name":"Technical Interview 1","description":"Core CS + Spring Boot deep dive."},{"name":"Technical Interview 2","description":"System design + coding round."},{"name":"HR Interview","description":"Culture fit and offer discussion."}]','["CSE","IT"]'),
  ('drv_cloudsphere','cmp_main','pc_main_2027','rec_cloudsphere','CloudSphere','Cloud Engineer','Hyderabad',9.20,7.5,0,2027,DATE '2026-09-19','ACTIVE','On-Campus','CloudSphere builds managed cloud infrastructure tooling. This role focuses on automation, deployment pipelines, and cloud-native architecture.',15,'Lab 2',NULL,NULL,'["AWS","Docker","Kubernetes","Linux","Python"]','[{"name":"Online Assessment","description":"Cloud fundamentals + scripting, 60 minutes."},{"name":"Technical Interview","description":"AWS + containerization scenarios."},{"name":"Managerial Round","description":"Ownership and collaboration signals."}]','["CSE","IT","ECE"]'),
  ('drv_datavision','cmp_main','pc_main_2027','rec_datavision','DataVision','Data Analyst','Pune',7.20,7.0,1,2027,DATE '2026-09-25','ACTIVE','On-Campus','DataVision helps enterprises turn raw data into decisions. Analysts here work across SQL pipelines, dashboards, and stakeholder reporting.',12,'Seminar Hall A',NULL,NULL,'["SQL","Python","Excel","Data Visualization","Statistics"]','[{"name":"Online Assessment","description":"SQL + analytical reasoning, 60 minutes."},{"name":"Case Study Round","description":"Live data interpretation exercise."},{"name":"HR Interview","description":"Culture fit and offer discussion."}]','["CSE","IT","ECE","EEE","MECH"]'),
  ('drv_innosoft','cmp_main','pc_main_2027','rec_innosoft','InnoSoft','Software Engineer','Chennai',6.80,7.0,1,2027,DATE '2026-09-30','ACTIVE','On-Campus','InnoSoft builds enterprise SaaS products. This role spans full-stack feature development across a modern web + cloud stack.',25,'Lab 1',NULL,NULL,'["JavaScript","React","Node.js","SQL","Git"]','[{"name":"Online Assessment","description":"Full-stack fundamentals, 75 minutes."},{"name":"Technical Interview","description":"Live coding + architecture discussion."},{"name":"HR Interview","description":"Culture fit and offer discussion."}]','["CSE","IT"]'),
  ('drv_quantflow','cmp_main','pc_main_2027','rec_quantflow','QuantFlow','Software Engineer (Platform)','Bengaluru',14.00,8.0,0,2027,DATE '2026-09-24','ACTIVE','On-Campus','QuantFlow runs low-latency trading infrastructure. Platform engineers here own performance-critical services end to end.',6,'Seminar Hall B',NULL,80,'["C++","Go","Algorithms","Linux","System Design"]','[{"name":"Online Assessment","description":"Advanced DSA, 120 minutes."},{"name":"Technical Interview 1","description":"Low-level systems and concurrency."},{"name":"Technical Interview 2","description":"Performance-oriented design round."},{"name":"Hiring Manager Round","description":"Ownership and depth discussion."}]','["CSE","IT"]'),
  ('drv_medisys','cmp_main','pc_main_2027','rec_medisys','MediSys','Embedded Systems Engineer','Bengaluru',6.40,6.5,1,2027,DATE '2026-09-26','ACTIVE','On-Campus','MediSys builds regulated medical devices. Engineers work on firmware, device telemetry and safety-critical validation.',10,'Lab 3',NULL,NULL,'["C","Embedded C","RTOS","Microcontrollers","Debugging"]','[{"name":"Technical Screening","description":"Embedded C and microcontroller fundamentals."},{"name":"Technical Interview","description":"Firmware debugging scenarios."},{"name":"HR Interview","description":"Culture fit and offer discussion."}]','["ECE","EEE"]'),
  ('drv_aerobuild','cmp_main','pc_main_2027','rec_aerobuild','AeroBuild','Design Engineer','Pune',5.20,6.5,2,2027,DATE '2026-09-28','ACTIVE','On-Campus','AeroBuild designs lightweight structural assemblies for aerospace suppliers. Design engineers own CAD models through to validation.',18,'Seminar Hall A',NULL,NULL,'["SolidWorks","AutoCAD","GD&T","Thermodynamics"]','[{"name":"Aptitude Test","description":"Quantitative and technical aptitude, 60 minutes."},{"name":"Technical Interview","description":"Design fundamentals and CAD walkthrough."},{"name":"HR Interview","description":"Culture fit and offer discussion."}]','["MECH","EEE"]'),
  ('drv_finedge','cmp_city','pc_city_2027','rec_finedge','FinEdge','Analyst — Risk Technology','Pune',7.80,7.0,0,2027,DATE '2026-09-27','ACTIVE','Virtual','FinEdge builds risk analytics for lending portfolios. Analysts blend SQL, Python and domain modelling.',8,'Virtual',NULL,NULL,'["SQL","Python","Statistics","Excel"]','[{"name":"Online Assessment","description":"Quant + SQL, 75 minutes."},{"name":"Video Interview","description":"Case discussion with a risk analyst."},{"name":"HR Interview","description":"Culture fit and offer discussion."}]','["CSE","IT","ECE"]'),
  ('drv_brightlearn','cmp_engg','pc_engg_2027','rec_brightlearn','BrightLearn','Full Stack Developer','Hyderabad',6.50,7.0,1,2027,DATE '2026-10-02','ACTIVE','Pooled','BrightLearn runs a large-scale learning platform. Full stack engineers ship features across React and Node services.',22,'Auditorium',NULL,NULL,'["React","Node.js","MongoDB","TypeScript"]','[{"name":"Online Assessment","description":"JavaScript + problem solving, 60 minutes."},{"name":"Technical Interview","description":"Full stack feature walkthrough."},{"name":"HR Interview","description":"Culture fit and offer discussion."}]','["CSE","IT","ECE"]'),
  ('drv_technova_intern','cmp_main','pc_main_2027','rec_technova','TechNova','Summer Internship — Backend','Bengaluru',NULL,7.0,1,2027,DATE '2026-03-10','COMPLETED','On-Campus','Completed summer internship cohort. High performers from this cohort are converted to PPO offers.',30,'Lab 1',NULL,NULL,'["Java","SQL","Git"]','[{"name":"Online Assessment","description":"Programming fundamentals, 60 minutes."},{"name":"Technical Interview","description":"Projects and fundamentals discussion."}]','["CSE","IT"]'),
  ('drv_cloudsphere_intern','cmp_main','pc_main_2027','rec_cloudsphere','CloudSphere','Cloud Internship','Hyderabad',NULL,7.0,1,2027,DATE '2026-03-12','COMPLETED','On-Campus','Completed cloud internship cohort. PPO decisions for this cohort are being released this month.',20,'Lab 3',NULL,NULL,'["Linux","Python","Networking"]','[{"name":"Online Assessment","description":"Scripting + networking basics, 45 minutes."},{"name":"Technical Interview","description":"Systems fundamentals discussion."}]','["CSE","IT","ECE"]'),
  ('drv_nimbus','cmp_main','pc_main_2027','rec_innosoft','InnoSoft','QA Automation Engineer','Chennai',5.60,6.5,2,2027,DATE '2026-10-08','DRAFT','On-Campus','Draft drive — eligibility criteria are still being finalised with the recruiter before publishing.',14,'Lab 1',NULL,NULL,'["Selenium","Java","API Testing"]','[{"name":"Online Assessment","description":"Testing fundamentals, 45 minutes."},{"name":"Technical Interview","description":"Automation framework discussion."}]','["CSE","IT","ECE"]')
ON CONFLICT (id) DO UPDATE SET
  campus_id = EXCLUDED.campus_id, cycle_id = EXCLUDED.cycle_id, recruiter_id = EXCLUDED.recruiter_id,
  company_name = EXCLUDED.company_name, role = EXCLUDED.role, location = EXCLUDED.location,
  package_lpa = EXCLUDED.package_lpa, minimum_cgpa = EXCLUDED.minimum_cgpa,
  maximum_backlogs = EXCLUDED.maximum_backlogs, graduation_year = EXCLUDED.graduation_year,
  deadline = EXCLUDED.deadline, status = EXCLUDED.status, drive_type = EXCLUDED.drive_type,
  description = EXCLUDED.description, expected_hiring = EXCLUDED.expected_hiring, venue = EXCLUDED.venue,
  minimum_tenth_percentage = EXCLUDED.minimum_tenth_percentage,
  minimum_twelfth_percentage = EXCLUDED.minimum_twelfth_percentage,
  required_skills_json = EXCLUDED.required_skills_json, rounds_json = EXCLUDED.rounds_json,
  allowed_branches_json = EXCLUDED.allowed_branches_json;

-- ---------- applications (funnel state per student x drive) ----------
INSERT INTO applications (id, student_id, drive_id, status, applied_on, cycle_id) VALUES
  ('app_1',  'stu_rahul', 'drv_technova',           'OFFER',       DATE '2026-08-20', 'pc_main_2027'),
  ('app_2',  'stu_rahul', 'drv_cloudsphere',        'INTERVIEW',   DATE '2026-08-22', 'pc_main_2027'),
  ('app_3',  'stu_rahul', 'drv_innosoft',           'SHORTLISTED', DATE '2026-08-26', 'pc_main_2027'),
  ('app_4',  'stu_rahul', 'drv_datavision',         'APPLIED',     DATE '2026-09-01', 'pc_main_2027'),
  ('app_5',  'stu_rahul', 'drv_quantflow',          'ELIGIBLE',    NULL,              'pc_main_2027'),
  ('app_6',  'stu_rahul', 'drv_technova_intern',    'JOINED',      DATE '2026-03-10', 'pc_main_2027'),
  ('app_7',  'stu_priya', 'drv_technova',           'SHORTLISTED', DATE '2026-08-19', 'pc_main_2027'),
  ('app_8',  'stu_arjun', 'drv_technova',           'ASSESSMENT',  DATE '2026-08-20', 'pc_main_2027'),
  ('app_9',  'stu_ankit', 'drv_technova',           'APPLIED',     DATE '2026-08-21', 'pc_main_2027'),
  ('app_10', 'stu_neha',  'drv_technova',           'APPLIED',     DATE '2026-08-23', 'pc_main_2027'),
  ('app_11', 'stu_kavya', 'drv_technova',           'SELECTED',    DATE '2026-08-18', 'pc_main_2027'),
  ('app_12', 'stu_priya', 'drv_cloudsphere',        'INTERVIEW',   DATE '2026-08-21', 'pc_main_2027'),
  ('app_13', 'stu_kavya', 'drv_quantflow',          'OFFER',       DATE '2026-08-24', 'pc_main_2027'),
  ('app_14', 'stu_arjun', 'drv_innosoft',           'SHORTLISTED', DATE '2026-08-27', 'pc_main_2027'),
  ('app_15', 'stu_ankit', 'drv_innosoft',           'APPLIED',     DATE '2026-08-28', 'pc_main_2027'),
  ('app_16', 'stu_neha',  'drv_datavision',         'APPLIED',     DATE '2026-09-02', 'pc_main_2027'),
  ('app_17', 'stu_sneha', 'drv_medisys',            'INTERVIEW',   DATE '2026-08-10', 'pc_main_2027'),
  ('app_18', 'stu_sneha', 'drv_datavision',         'REJECTED',    DATE '2026-08-05', 'pc_main_2027'),
  ('app_19', 'stu_amit',  'drv_aerobuild',          'APPLIED',     DATE '2026-08-29', 'pc_main_2027'),
  ('app_20', 'stu_isha',  'drv_finedge',            'SHORTLISTED', DATE '2026-08-25', 'pc_city_2027'),
  ('app_21', 'stu_isha',  'drv_datavision',         'APPLIED',     DATE '2026-08-30', 'pc_main_2027'),
  ('app_22', 'stu_rohit', 'drv_brightlearn',        'APPLIED',     DATE '2026-09-01', 'pc_engg_2027'),
  ('app_23', 'stu_meera', 'drv_brightlearn',        'SHORTLISTED', DATE '2026-08-31', 'pc_engg_2027'),
  ('app_24', 'stu_meera', 'drv_datavision',         'ASSESSMENT',  DATE '2026-09-02', 'pc_main_2027'),
  ('app_25', 'stu_farhan','drv_aerobuild',          'APPLIED',     DATE '2026-09-03', 'pc_main_2027'),
  ('app_26', 'stu_priya', 'drv_cloudsphere_intern', 'JOINED',      DATE '2026-03-12', 'pc_main_2027')
ON CONFLICT (id) DO UPDATE SET
  student_id = EXCLUDED.student_id, drive_id = EXCLUDED.drive_id,
  status = EXCLUDED.status, applied_on = EXCLUDED.applied_on, cycle_id = EXCLUDED.cycle_id;

-- ---------- application_status_history (funnel audit trail; V36) ----------
-- Full transition chains for the applications that advanced through the funnel;
-- a single origin row for applications still at their entry stage. changed_by is
-- free text (the officer/system that drove the transition).
INSERT INTO application_status_history (id, application_id, previous_status, new_status, changed_by, changed_at) VALUES
  -- app_1: Rahul @ TechNova, walked the full funnel to OFFER
  ('apphist_1a','app_1',NULL,'APPLIED','stu_rahul',TIMESTAMPTZ '2026-08-20 09:00:00+05:30'),
  ('apphist_1b','app_1','APPLIED','SHORTLISTED','officer',TIMESTAMPTZ '2026-08-28 11:00:00+05:30'),
  ('apphist_1c','app_1','SHORTLISTED','ASSESSMENT','officer',TIMESTAMPTZ '2026-09-02 10:00:00+05:30'),
  ('apphist_1d','app_1','ASSESSMENT','INTERVIEW','officer',TIMESTAMPTZ '2026-09-08 10:00:00+05:30'),
  ('apphist_1e','app_1','INTERVIEW','SELECTED','officer',TIMESTAMPTZ '2026-09-15 16:00:00+05:30'),
  ('apphist_1f','app_1','SELECTED','OFFER','officer',TIMESTAMPTZ '2026-09-18 10:00:00+05:30'),
  -- app_11: Kavya @ TechNova, selected
  ('apphist_11a','app_11',NULL,'APPLIED','stu_kavya',TIMESTAMPTZ '2026-08-18 09:00:00+05:30'),
  ('apphist_11b','app_11','APPLIED','SHORTLISTED','officer',TIMESTAMPTZ '2026-08-22 11:00:00+05:30'),
  ('apphist_11c','app_11','SHORTLISTED','INTERVIEW','officer',TIMESTAMPTZ '2026-09-01 10:00:00+05:30'),
  ('apphist_11d','app_11','INTERVIEW','SELECTED','officer',TIMESTAMPTZ '2026-09-16 16:00:00+05:30'),
  -- app_13: Kavya @ QuantFlow, offer
  ('apphist_13a','app_13',NULL,'APPLIED','stu_kavya',TIMESTAMPTZ '2026-08-24 09:00:00+05:30'),
  ('apphist_13b','app_13','APPLIED','INTERVIEW','officer',TIMESTAMPTZ '2026-08-30 10:00:00+05:30'),
  ('apphist_13c','app_13','INTERVIEW','SELECTED','officer',TIMESTAMPTZ '2026-09-02 16:00:00+05:30'),
  ('apphist_13d','app_13','SELECTED','OFFER','officer',TIMESTAMPTZ '2026-09-04 10:00:00+05:30'),
  -- app_6: Rahul, internship converted (JOINED)
  ('apphist_6a','app_6',NULL,'APPLIED','stu_rahul',TIMESTAMPTZ '2026-01-10 09:00:00+05:30'),
  ('apphist_6b','app_6','APPLIED','SELECTED','officer',TIMESTAMPTZ '2026-02-20 10:00:00+05:30'),
  ('apphist_6c','app_6','SELECTED','OFFER','officer',TIMESTAMPTZ '2026-03-01 10:00:00+05:30'),
  ('apphist_6d','app_6','OFFER','JOINED','officer',TIMESTAMPTZ '2026-03-10 10:00:00+05:30'),
  -- app_26: Priya, internship converted (JOINED)
  ('apphist_26a','app_26',NULL,'APPLIED','stu_priya',TIMESTAMPTZ '2026-01-12 09:00:00+05:30'),
  ('apphist_26b','app_26','APPLIED','SELECTED','officer',TIMESTAMPTZ '2026-02-22 10:00:00+05:30'),
  ('apphist_26c','app_26','SELECTED','OFFER','officer',TIMESTAMPTZ '2026-03-02 10:00:00+05:30'),
  ('apphist_26d','app_26','OFFER','JOINED','officer',TIMESTAMPTZ '2026-03-12 10:00:00+05:30'),
  -- app_18: Sneha @ DataVision, rejected after case round
  ('apphist_18a','app_18',NULL,'APPLIED','stu_sneha',TIMESTAMPTZ '2026-08-05 09:00:00+05:30'),
  ('apphist_18b','app_18','APPLIED','INTERVIEW','officer',TIMESTAMPTZ '2026-08-11 10:00:00+05:30'),
  ('apphist_18c','app_18','INTERVIEW','REJECTED','officer',TIMESTAMPTZ '2026-08-13 12:00:00+05:30'),
  -- Single origin rows for applications still at their entry/current stage
  ('apphist_2','app_2',NULL,'INTERVIEW','officer',TIMESTAMPTZ '2026-08-22 09:00:00+05:30'),
  ('apphist_3','app_3',NULL,'SHORTLISTED','officer',TIMESTAMPTZ '2026-08-26 09:00:00+05:30'),
  ('apphist_4','app_4',NULL,'APPLIED','stu_rahul',TIMESTAMPTZ '2026-09-01 09:00:00+05:30'),
  ('apphist_5','app_5',NULL,'ELIGIBLE','system',TIMESTAMPTZ '2026-08-15 09:00:00+05:30'),
  ('apphist_7','app_7',NULL,'SHORTLISTED','officer',TIMESTAMPTZ '2026-08-19 09:00:00+05:30'),
  ('apphist_8','app_8',NULL,'ASSESSMENT','officer',TIMESTAMPTZ '2026-08-20 09:00:00+05:30'),
  ('apphist_9','app_9',NULL,'APPLIED','stu_ankit',TIMESTAMPTZ '2026-08-21 09:00:00+05:30'),
  ('apphist_10','app_10',NULL,'APPLIED','stu_neha',TIMESTAMPTZ '2026-08-23 09:00:00+05:30'),
  ('apphist_12','app_12',NULL,'INTERVIEW','officer',TIMESTAMPTZ '2026-08-21 09:00:00+05:30'),
  ('apphist_14','app_14',NULL,'SHORTLISTED','officer',TIMESTAMPTZ '2026-08-27 09:00:00+05:30'),
  ('apphist_15','app_15',NULL,'APPLIED','stu_ankit',TIMESTAMPTZ '2026-08-28 09:00:00+05:30'),
  ('apphist_16','app_16',NULL,'APPLIED','stu_neha',TIMESTAMPTZ '2026-09-02 09:00:00+05:30'),
  ('apphist_17','app_17',NULL,'INTERVIEW','officer',TIMESTAMPTZ '2026-08-10 09:00:00+05:30'),
  ('apphist_19','app_19',NULL,'APPLIED','stu_amit',TIMESTAMPTZ '2026-08-29 09:00:00+05:30'),
  ('apphist_20','app_20',NULL,'SHORTLISTED','officer',TIMESTAMPTZ '2026-08-25 09:00:00+05:30'),
  ('apphist_21','app_21',NULL,'APPLIED','stu_isha',TIMESTAMPTZ '2026-08-30 09:00:00+05:30'),
  ('apphist_22','app_22',NULL,'APPLIED','stu_rohit',TIMESTAMPTZ '2026-09-01 09:00:00+05:30'),
  ('apphist_23','app_23',NULL,'SHORTLISTED','officer',TIMESTAMPTZ '2026-08-31 09:00:00+05:30'),
  ('apphist_24','app_24',NULL,'ASSESSMENT','officer',TIMESTAMPTZ '2026-09-02 09:00:00+05:30'),
  ('apphist_25','app_25',NULL,'APPLIED','stu_farhan',TIMESTAMPTZ '2026-09-03 09:00:00+05:30')
ON CONFLICT (id) DO UPDATE SET
  application_id = EXCLUDED.application_id, previous_status = EXCLUDED.previous_status,
  new_status = EXCLUDED.new_status, changed_by = EXCLUDED.changed_by, changed_at = EXCLUDED.changed_at;

-- ---------- match_results (persisted explainable AI scoring; V35) ----------
-- One row per (drive, student, model_version). model_version matches the app's
-- ml.policy-version default ('policy-2026-01') so a live GET /matches upserts
-- these same keys rather than duplicating. The *_json columns carry the
-- explanation exactly as the engine emits (breakdown = per-feature scores,
-- why_selected = strengths, weak_or_missing = gaps).
INSERT INTO match_results (
  id, campus_id, cycle_id, student_id, drive_id, overall_fit, eligibility_passed,
  breakdown_json, why_selected_json, weak_or_missing_json, recommendation, model_version, computed_at
) VALUES
  ('matchres_priya_tn','cmp_main','pc_main_2027','stu_priya','drv_technova',91,true,'{"requiredSkillCoverage":88,"semanticSimilarity":90,"projects":86,"readiness":85,"interview":80,"academics":92}','["Strong Spring Boot + system design depth","Distributed systems projects align with the role","Readiness 85 and CGPA 8.92 clear the bar comfortably"]','["Kubernetes exposure is thin for cloud-native work"]','STRONG_FIT','policy-2026-01',TIMESTAMPTZ '2026-09-12 09:00:00+05:30'),
  ('matchres_rahul_tn','cmp_main','pc_main_2027','stu_rahul','drv_technova',87,true,'{"requiredSkillCoverage":90,"semanticSimilarity":85,"projects":88,"readiness":78,"interview":72,"academics":86}','["Covers 5 of 6 required skills including Java, Spring Boot, SQL","Payments API project maps directly to fintech backend work"]','["Docker/AWS depth is limited","Interview readiness (72) trails the top candidates"]','STRONG_FIT','policy-2026-01',TIMESTAMPTZ '2026-09-12 09:00:00+05:30'),
  ('matchres_arjun_tn','cmp_main','pc_main_2027','stu_arjun','drv_technova',82,true,'{"requiredSkillCoverage":80,"semanticSimilarity":83,"projects":82,"readiness":81,"interview":75,"academics":84}','["Kafka + Spring Boot streaming project is relevant","Solid academics and steady readiness trend"]','["No AWS experience listed","System design still developing"]','GOOD_FIT','policy-2026-01',TIMESTAMPTZ '2026-09-12 09:00:00+05:30'),
  ('matchres_ankit_tn','cmp_main','pc_main_2027','stu_ankit','drv_technova',74,true,'{"requiredSkillCoverage":72,"semanticSimilarity":75,"projects":81,"readiness":79,"interview":74,"academics":76}','["Microservices project demonstrates API + auth fundamentals"]','["Missing Docker and AWS from required skills","No certifications on record"]','GOOD_FIT','policy-2026-01',TIMESTAMPTZ '2026-09-12 09:00:00+05:30'),
  ('matchres_neha_tn','cmp_main','pc_main_2027','stu_neha','drv_technova',69,true,'{"requiredSkillCoverage":70,"semanticSimilarity":72,"projects":77,"readiness":76,"interview":66,"academics":82}','["Core Java + REST fundamentals present"]','["Interview conversion below benchmark (66)","No Docker/System Design depth"]','BORDERLINE','policy-2026-01',TIMESTAMPTZ '2026-09-12 09:00:00+05:30'),
  ('matchres_kavya_tn','cmp_main','pc_main_2027','stu_kavya','drv_technova',88,true,'{"requiredSkillCoverage":78,"semanticSimilarity":82,"projects":87,"readiness":88,"interview":86,"academics":94}','["Exceptional academics and interview performance","Systems project (Raft KV store) signals strong fundamentals"]','["Skill overlap is in C++/Go rather than the Java stack"]','STRONG_FIT','policy-2026-01',TIMESTAMPTZ '2026-09-12 09:00:00+05:30'),
  ('matchres_kavya_qf','cmp_main','pc_main_2027','stu_kavya','drv_quantflow',93,true,'{"requiredSkillCoverage":92,"semanticSimilarity":91,"projects":87,"readiness":88,"interview":86,"academics":94}','["C++/Go + algorithms match the platform role exactly","Low-level systems project is directly relevant"]','["Limited exposure to production trading systems"]','STRONG_FIT','policy-2026-01',TIMESTAMPTZ '2026-09-02 09:00:00+05:30'),
  ('matchres_isha_fe','cmp_city','pc_city_2027','stu_isha','drv_finedge',79,true,'{"requiredSkillCoverage":75,"semanticSimilarity":78,"projects":71,"readiness":73,"interview":72,"academics":82}','["Python + data visualization align with risk analytics","Strong communication for stakeholder-facing work"]','["SQL depth needs strengthening for the role"]','GOOD_FIT','policy-2026-01',TIMESTAMPTZ '2026-08-24 09:00:00+05:30'),
  ('matchres_meera_bl','cmp_engg','pc_engg_2027','stu_meera','drv_brightlearn',76,true,'{"requiredSkillCoverage":68,"semanticSimilarity":74,"projects":81,"readiness":80,"interview":76,"academics":86}','["Strong CS fundamentals and steady readiness","ML/SQL background transfers to full-stack data features"]','["React/Node not on the current skill profile","TypeScript exposure unknown"]','BORDERLINE','policy-2026-01',TIMESTAMPTZ '2026-08-31 09:00:00+05:30')
ON CONFLICT (drive_id, student_id, model_version) DO UPDATE SET
  campus_id = EXCLUDED.campus_id, cycle_id = EXCLUDED.cycle_id, overall_fit = EXCLUDED.overall_fit,
  eligibility_passed = EXCLUDED.eligibility_passed, breakdown_json = EXCLUDED.breakdown_json,
  why_selected_json = EXCLUDED.why_selected_json, weak_or_missing_json = EXCLUDED.weak_or_missing_json,
  recommendation = EXCLUDED.recommendation, computed_at = EXCLUDED.computed_at;

-- ---------- shortlist_decisions (officer accept/reject over a scored match; V35) ----------
INSERT INTO shortlist_decisions (
  id, match_result_id, drive_id, student_id, decided_by, decision, reason, decided_at
) VALUES
  ('shortlist_priya_tn','matchres_priya_tn','drv_technova','stu_priya','Officer123@gmail.com','SHORTLIST','Top fit for the backend role — advance to assessment.',TIMESTAMPTZ '2026-08-19 11:00:00+05:30'),
  ('shortlist_rahul_tn','matchres_rahul_tn','drv_technova','stu_rahul','Officer123@gmail.com','SHORTLIST','Strong skill coverage; move forward.',TIMESTAMPTZ '2026-08-28 11:00:00+05:30'),
  ('shortlist_arjun_is','matchres_arjun_tn','drv_innosoft','stu_arjun','Officer123@gmail.com','SHORTLIST','Relevant streaming project; shortlist for InnoSoft.',TIMESTAMPTZ '2026-08-27 10:00:00+05:30'),
  ('shortlist_neha_tn','matchres_neha_tn','drv_technova','stu_neha','Officer123@gmail.com','REJECT','Interview readiness below benchmark for this cohort.',TIMESTAMPTZ '2026-08-29 10:00:00+05:30')
ON CONFLICT (id) DO UPDATE SET
  match_result_id = EXCLUDED.match_result_id, drive_id = EXCLUDED.drive_id, student_id = EXCLUDED.student_id,
  decided_by = EXCLUDED.decided_by, decision = EXCLUDED.decision, reason = EXCLUDED.reason,
  decided_at = EXCLUDED.decided_at;

-- ---------- interviews ----------
INSERT INTO interviews (
  id, campus_id, student_id, drive_id, schedule_event_id, mode, scheduled_at, status, result,
  feedback, created_at, round, venue, panel, company_name
) VALUES
  ('iv_rahul_tn_oa','cmp_main','stu_rahul','drv_technova',NULL,'VIRTUAL',TIMESTAMPTZ '2026-09-08 10:00:00+05:30','COMPLETED','SELECTED',NULL,TIMESTAMPTZ '2026-09-01 09:00:00+05:30','Online Assessment','Online proctored','Automated','TechNova'),
  ('iv_rahul_tn_t1','cmp_main','stu_rahul','drv_technova',NULL,'ON_CAMPUS',TIMESTAMPTZ '2026-09-22 10:30:00+05:30','SCHEDULED','PENDING',NULL,TIMESTAMPTZ '2026-09-01 09:00:00+05:30','Technical Interview 1','Lab 2','Panel A — Dr. Anita Mishra, R. Iyer','TechNova'),
  ('iv_rahul_cs_t1','cmp_main','stu_rahul','drv_cloudsphere',NULL,'ON_CAMPUS',TIMESTAMPTZ '2026-09-22 14:30:00+05:30','SCHEDULED','PENDING',NULL,TIMESTAMPTZ '2026-09-01 09:00:00+05:30','Technical Interview','Lab 3','Panel C — Prof. Rajesh Nair','CloudSphere'),
  ('iv_rahul_is_oa','cmp_main','stu_rahul','drv_innosoft',NULL,'VIRTUAL',TIMESTAMPTZ '2026-09-28 11:00:00+05:30','SCHEDULED','PENDING',NULL,TIMESTAMPTZ '2026-09-01 09:00:00+05:30','Online Assessment','Online proctored','Automated','InnoSoft'),
  ('iv_priya_tn_t1','cmp_main','stu_priya','drv_technova',NULL,'ON_CAMPUS',TIMESTAMPTZ '2026-09-22 11:15:00+05:30','SCHEDULED','PENDING',NULL,TIMESTAMPTZ '2026-09-01 09:00:00+05:30','Technical Interview 1','Lab 2','Panel A — Dr. Anita Mishra, R. Iyer','TechNova'),
  ('iv_arjun_tn_oa','cmp_main','stu_arjun','drv_technova',NULL,'VIRTUAL',TIMESTAMPTZ '2026-09-19 10:00:00+05:30','SCHEDULED','PENDING',NULL,TIMESTAMPTZ '2026-09-01 09:00:00+05:30','Online Assessment','Online proctored','Automated','TechNova'),
  ('iv_kavya_tn_hr','cmp_main','stu_kavya','drv_technova',NULL,'VIRTUAL',TIMESTAMPTZ '2026-09-16 15:00:00+05:30','COMPLETED','SELECTED',NULL,TIMESTAMPTZ '2026-09-01 09:00:00+05:30','HR Interview','Video call','Panel B — TechNova HR','TechNova'),
  ('iv_kavya_qf_hm','cmp_main','stu_kavya','drv_quantflow',NULL,'VIRTUAL',TIMESTAMPTZ '2026-09-02 16:00:00+05:30','COMPLETED','SELECTED',NULL,TIMESTAMPTZ '2026-09-01 09:00:00+05:30','Hiring Manager Round','Video call','QuantFlow Engineering','QuantFlow'),
  ('iv_sneha_ms_t1','cmp_main','stu_sneha','drv_medisys',NULL,'ON_CAMPUS',TIMESTAMPTZ '2026-09-30 11:30:00+05:30','SCHEDULED','PENDING',NULL,TIMESTAMPTZ '2026-09-01 09:00:00+05:30','Technical Interview','Lab 3','Panel D — MediSys Firmware','MediSys'),
  ('iv_sneha_dv_case','cmp_main','stu_sneha','drv_datavision',NULL,'ON_CAMPUS',TIMESTAMPTZ '2026-08-12 10:30:00+05:30','COMPLETED','REJECTED',NULL,TIMESTAMPTZ '2026-08-01 09:00:00+05:30','Case Study Round','Seminar Hall A','Panel B — DataVision Analytics','DataVision'),
  ('iv_isha_fe_video','cmp_city','stu_isha','drv_finedge',NULL,'VIRTUAL',TIMESTAMPTZ '2026-10-04 14:00:00+05:30','SCHEDULED','PENDING',NULL,TIMESTAMPTZ '2026-09-01 09:00:00+05:30','Video Interview','Video call','FinEdge Risk','FinEdge'),
  ('iv_meera_bl_t1','cmp_engg','stu_meera','drv_brightlearn',NULL,'ON_CAMPUS',TIMESTAMPTZ '2026-10-08 10:00:00+05:30','SCHEDULED','PENDING',NULL,TIMESTAMPTZ '2026-09-01 09:00:00+05:30','Technical Interview','Auditorium','BrightLearn Platform','BrightLearn'),
  ('iv_neha_tn_oa','cmp_main','stu_neha','drv_technova',NULL,'VIRTUAL',TIMESTAMPTZ '2026-09-19 10:00:00+05:30','SCHEDULED','PENDING',NULL,TIMESTAMPTZ '2026-09-01 09:00:00+05:30','Online Assessment','Online proctored','Automated','TechNova'),
  ('iv_ankit_tn_oa','cmp_main','stu_ankit','drv_technova',NULL,'VIRTUAL',TIMESTAMPTZ '2026-09-19 10:00:00+05:30','SCHEDULED','PENDING',NULL,TIMESTAMPTZ '2026-09-01 09:00:00+05:30','Online Assessment','Online proctored','Automated','TechNova')
ON CONFLICT (id) DO UPDATE SET
  campus_id = EXCLUDED.campus_id, student_id = EXCLUDED.student_id, drive_id = EXCLUDED.drive_id,
  schedule_event_id = EXCLUDED.schedule_event_id, mode = EXCLUDED.mode, scheduled_at = EXCLUDED.scheduled_at,
  status = EXCLUDED.status, result = EXCLUDED.result, feedback = EXCLUDED.feedback,
  created_at = EXCLUDED.created_at, round = EXCLUDED.round, venue = EXCLUDED.venue,
  panel = EXCLUDED.panel, company_name = EXCLUDED.company_name;

-- ---------- offers (incl. PPOs from internships + mixed statuses) ----------
INSERT INTO offers (
  id, campus_id, cycle_id, student_id, drive_id, recruiter_id, role, package_lpa, status, extended_at,
  decided_at, decided_by, company_name, offer_type, location, joining_date, acceptance_deadline,
  documents_verified, joined, from_internship
) VALUES
  ('off_rahul_technova','cmp_main','pc_main_2027','stu_rahul','drv_technova','rec_technova','Backend Developer',8.50,'EXTENDED',TIMESTAMPTZ '2026-09-10 10:00:00+05:30',NULL,NULL,'TechNova','Full-Time','Bengaluru',DATE '2027-07-15',DATE '2026-09-24',false,false,NULL),
  ('off_rahul_cloudsphere_ppo','cmp_main','pc_main_2027','stu_rahul','drv_cloudsphere_intern','rec_cloudsphere','Cloud Engineer (PPO)',7.80,'EXTENDED',TIMESTAMPTZ '2026-09-06 10:00:00+05:30',NULL,NULL,'CloudSphere','PPO','Hyderabad',DATE '2027-08-01',DATE '2026-09-26',false,false,'CloudSphere Cloud Internship (Mar – Jun 2026)'),
  ('off_kavya_quantflow','cmp_main','pc_main_2027','stu_kavya','drv_quantflow','rec_quantflow','Software Engineer (Platform)',14.00,'ACCEPTED',TIMESTAMPTZ '2026-09-04 10:00:00+05:30',TIMESTAMPTZ '2026-09-05 10:00:00+05:30','student','QuantFlow','Full-Time','Bengaluru',DATE '2027-07-01',DATE '2026-09-18',true,false,NULL),
  ('off_priya_cloudsphere_ppo','cmp_main','pc_main_2027','stu_priya','drv_cloudsphere_intern','rec_cloudsphere','Cloud Engineer (PPO)',9.20,'ACCEPTED',TIMESTAMPTZ '2026-09-02 10:00:00+05:30',TIMESTAMPTZ '2026-09-03 10:00:00+05:30','student','CloudSphere','PPO','Hyderabad',DATE '2027-08-01',DATE '2026-09-16',true,true,'CloudSphere Cloud Internship (Mar – Jun 2026)'),
  ('off_isha_finedge','cmp_city','pc_city_2027','stu_isha','drv_finedge','rec_finedge','Analyst — Risk Technology',7.80,'EXTENDED',TIMESTAMPTZ '2026-09-08 10:00:00+05:30',NULL,NULL,'FinEdge','Full-Time','Pune',DATE '2027-07-20',DATE '2026-09-22',false,false,NULL),
  ('off_meera_brightlearn','cmp_engg','pc_engg_2027','stu_meera','drv_brightlearn','rec_brightlearn','Full Stack Developer',6.50,'DECLINED',TIMESTAMPTZ '2026-09-05 10:00:00+05:30',TIMESTAMPTZ '2026-09-06 10:00:00+05:30','student','BrightLearn','Full-Time','Hyderabad',DATE '2027-07-10',DATE '2026-09-19',false,false,NULL),
  ('off_arjun_innosoft','cmp_main','pc_main_2027','stu_arjun','drv_innosoft','rec_innosoft','Software Engineer',6.80,'ACCEPTED',TIMESTAMPTZ '2026-09-09 10:00:00+05:30',TIMESTAMPTZ '2026-09-10 10:00:00+05:30','student','InnoSoft','Full-Time','Chennai',DATE '2027-07-15',DATE '2026-09-23',false,false,NULL)
ON CONFLICT (id) DO UPDATE SET
  campus_id = EXCLUDED.campus_id, cycle_id = EXCLUDED.cycle_id, student_id = EXCLUDED.student_id,
  drive_id = EXCLUDED.drive_id, recruiter_id = EXCLUDED.recruiter_id, role = EXCLUDED.role,
  package_lpa = EXCLUDED.package_lpa, status = EXCLUDED.status, extended_at = EXCLUDED.extended_at,
  decided_at = EXCLUDED.decided_at, decided_by = EXCLUDED.decided_by, company_name = EXCLUDED.company_name,
  offer_type = EXCLUDED.offer_type, location = EXCLUDED.location, joining_date = EXCLUDED.joining_date,
  acceptance_deadline = EXCLUDED.acceptance_deadline, documents_verified = EXCLUDED.documents_verified,
  joined = EXCLUDED.joined, from_internship = EXCLUDED.from_internship;

-- ---------- readiness_snapshots (derived readiness history; V15 + cycle_id V34) ----------
-- placement_cycle_id keeps the legacy string SoR; cycle_id is the new FK (both point
-- at the student's campus cycle). One current snapshot per student.
INSERT INTO readiness_snapshots (
  id, student_id, campus_id, placement_cycle_id, cycle_id, score, category, component_scores_json,
  evidence_ids, confidence, calculation_version, model_version, feature_version, generated_at, created_at
) VALUES
  ('rs_rahul','stu_rahul','cmp_main','pc_main_2027','pc_main_2027',78,'ON_TRACK','{"academic":86,"technical":82,"coding":74,"aptitude":79,"communication":69,"projects":88,"interview":72,"placementActivity":76}',NULL,0.82,'calc-2026-01','readiness-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('rs_priya','stu_priya','cmp_main','pc_main_2027','pc_main_2027',85,'READY','{"academic":92,"technical":88,"coding":85,"aptitude":88,"communication":83,"projects":86,"interview":80,"placementActivity":78}',NULL,0.88,'calc-2026-01','readiness-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('rs_arjun','stu_arjun','cmp_main','pc_main_2027','pc_main_2027',81,'READY','{"academic":84,"technical":83,"coding":79,"aptitude":80,"communication":76,"projects":82,"interview":75,"placementActivity":79}',NULL,0.84,'calc-2026-01','readiness-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('rs_ankit','stu_ankit','cmp_main','pc_main_2027','pc_main_2027',79,'ON_TRACK','{"academic":76,"technical":80,"coding":75,"aptitude":76,"communication":78,"projects":81,"interview":74,"placementActivity":80}',NULL,0.80,'calc-2026-01','readiness-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('rs_neha','stu_neha','cmp_main','pc_main_2027','pc_main_2027',76,'ON_TRACK','{"academic":82,"technical":78,"coding":72,"aptitude":74,"communication":74,"projects":77,"interview":69,"placementActivity":72}',NULL,0.79,'calc-2026-01','readiness-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('rs_kavya','stu_kavya','cmp_main','pc_main_2027','pc_main_2027',88,'READY','{"academic":94,"technical":90,"coding":94,"aptitude":92,"communication":84,"projects":87,"interview":86,"placementActivity":77}',NULL,0.90,'calc-2026-01','readiness-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('rs_sneha','stu_sneha','cmp_main','pc_main_2027','pc_main_2027',51,'NEEDS_SUPPORT','{"academic":68,"technical":55,"coding":52,"aptitude":60,"communication":50,"projects":54,"interview":28,"placementActivity":41}',NULL,0.75,'calc-2026-01','readiness-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('rs_aman','stu_aman','cmp_main','pc_main_2027','pc_main_2027',46,'AT_RISK','{"academic":58,"technical":46,"coding":41,"aptitude":48,"communication":54,"projects":44,"interview":38,"placementActivity":22}',NULL,0.72,'calc-2026-01','readiness-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('rs_amit','stu_amit','cmp_main','pc_main_2027','pc_main_2027',43,'AT_RISK','{"academic":52,"technical":38,"coding":30,"aptitude":42,"communication":45,"projects":35,"interview":30,"placementActivity":26}',NULL,0.70,'calc-2026-01','readiness-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('rs_rohit','stu_rohit','cmp_city','pc_city_2027','pc_city_2027',64,'NEEDS_SUPPORT','{"academic":72,"technical":64,"coding":58,"aptitude":66,"communication":70,"projects":62,"interview":60,"placementActivity":58}',NULL,0.77,'calc-2026-01','readiness-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('rs_isha','stu_isha','cmp_city','pc_city_2027','pc_city_2027',73,'ON_TRACK','{"academic":82,"technical":74,"coding":70,"aptitude":78,"communication":80,"projects":71,"interview":72,"placementActivity":66}',NULL,0.81,'calc-2026-01','readiness-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('rs_vikram','stu_vikram','cmp_city','pc_city_2027','pc_city_2027',48,'AT_RISK','{"academic":56,"technical":48,"coding":34,"aptitude":45,"communication":52,"projects":46,"interview":36,"placementActivity":28}',NULL,0.71,'calc-2026-01','readiness-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('rs_meera','stu_meera','cmp_engg','pc_engg_2027','pc_engg_2027',80,'READY','{"academic":86,"technical":80,"coding":78,"aptitude":84,"communication":79,"projects":81,"interview":76,"placementActivity":74}',NULL,0.83,'calc-2026-01','readiness-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('rs_farhan','stu_farhan','cmp_engg','pc_engg_2027','pc_engg_2027',58,'NEEDS_SUPPORT','{"academic":70,"technical":60,"coding":40,"aptitude":58,"communication":64,"projects":52,"interview":52,"placementActivity":48}',NULL,0.74,'calc-2026-01','readiness-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30')
ON CONFLICT (id) DO UPDATE SET
  student_id = EXCLUDED.student_id, campus_id = EXCLUDED.campus_id,
  placement_cycle_id = EXCLUDED.placement_cycle_id, cycle_id = EXCLUDED.cycle_id, score = EXCLUDED.score,
  category = EXCLUDED.category, component_scores_json = EXCLUDED.component_scores_json,
  confidence = EXCLUDED.confidence, calculation_version = EXCLUDED.calculation_version,
  model_version = EXCLUDED.model_version, feature_version = EXCLUDED.feature_version,
  generated_at = EXCLUDED.generated_at, created_at = EXCLUDED.created_at;

-- ---------- risk_entries (placement-risk register; V16 + cycle_id V34) ----------
-- level is a plain string (no enum): HIGH / MEDIUM / LOW. support_probability is the
-- modelled likelihood of a good outcome given intervention.
INSERT INTO risk_entries (
  id, student_id, campus_id, placement_cycle_id, cycle_id, level, support_probability, primary_reason,
  factors_json, recommended_interventions_json, confidence, model_version, feature_version, generated_at, created_at
) VALUES
  ('risk_amit','stu_amit','cmp_main','pc_main_2027','pc_main_2027','HIGH',0.38,'Low coding proficiency and minimal placement activity',
   '[{"factor":"coding","impact":"high"},{"factor":"placementActivity","impact":"high"},{"factor":"projects","impact":"medium"}]',
   '["Assign to intensive DSA bootcamp","Weekly 1:1 mentor check-ins","Mandatory 2 mock interviews before applying"]',0.70,'risk-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('risk_aman','stu_aman','cmp_main','pc_main_2027','pc_main_2027','HIGH',0.44,'Weak technical foundation, very low applications submitted',
   '[{"factor":"technical","impact":"high"},{"factor":"placementActivity","impact":"high"},{"factor":"aptitude","impact":"medium"}]',
   '["Enrol in core CS refresher","Set weekly application target of 5 drives","Pair with a placed senior mentor"]',0.72,'risk-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('risk_vikram','stu_vikram','cmp_city','pc_city_2027','pc_city_2027','HIGH',0.41,'Coding and interview readiness well below cohort',
   '[{"factor":"coding","impact":"high"},{"factor":"interview","impact":"high"},{"factor":"placementActivity","impact":"medium"}]',
   '["Daily coding practice plan","Communication + mock interview workshop","Reduce backlog load with faculty support"]',0.71,'risk-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('risk_sneha','stu_sneha','cmp_main','pc_main_2027','pc_main_2027','MEDIUM',0.58,'Interview performance dragging an otherwise solid profile',
   '[{"factor":"interview","impact":"high"},{"factor":"communication","impact":"medium"}]',
   '["Two structured mock interviews","Behavioural-round coaching"]',0.76,'risk-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('risk_rohit','stu_rohit','cmp_city','pc_city_2027','pc_city_2027','MEDIUM',0.61,'Consistent but below-threshold across coding and activity',
   '[{"factor":"coding","impact":"medium"},{"factor":"placementActivity","impact":"medium"}]',
   '["Targeted DSA topic revision","Increase weekly application volume"]',0.77,'risk-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('risk_farhan','stu_farhan','cmp_engg','pc_engg_2027','pc_engg_2027','MEDIUM',0.60,'Coding gap limiting eligibility for core roles',
   '[{"factor":"coding","impact":"high"},{"factor":"projects","impact":"medium"}]',
   '["Focused problem-solving track","Build one portfolio project this cycle"]',0.74,'risk-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30'),
  ('risk_neha','stu_neha','cmp_main','pc_main_2027','pc_main_2027','LOW',0.82,'On track; monitor interview conversion',
   '[{"factor":"interview","impact":"low"}]',
   '["Continue mock interview cadence"]',0.79,'risk-2026-01','feat-1',TIMESTAMPTZ '2026-09-15 08:00:00+05:30',TIMESTAMPTZ '2026-09-15 08:00:00+05:30')
ON CONFLICT (id) DO UPDATE SET
  student_id = EXCLUDED.student_id, campus_id = EXCLUDED.campus_id,
  placement_cycle_id = EXCLUDED.placement_cycle_id, cycle_id = EXCLUDED.cycle_id, level = EXCLUDED.level,
  support_probability = EXCLUDED.support_probability, primary_reason = EXCLUDED.primary_reason,
  factors_json = EXCLUDED.factors_json, recommended_interventions_json = EXCLUDED.recommended_interventions_json,
  confidence = EXCLUDED.confidence, model_version = EXCLUDED.model_version, feature_version = EXCLUDED.feature_version,
  generated_at = EXCLUDED.generated_at, created_at = EXCLUDED.created_at;

-- ---------- notifications (V11) ----------
-- recipient_username is FK-constrained to user_accounts (V33). After V29 the only seeded
-- account is 'Officer123@gmail.com', so every demo notification targets the officer.
INSERT INTO notifications (
  id, campus_id, recipient_username, channel, subject, body, status, attempt_count, created_at, last_attempt_at, read_at
) VALUES
  ('ntf_shortlist_technova','cmp_main','Officer123@gmail.com','IN_APP','Shortlist approved: TechNova SDE',
   'Priya, Rahul and Arjun were shortlisted for the TechNova Software Engineer drive. Neha was declined.',
   'SENT',1,TIMESTAMPTZ '2026-09-16 09:30:00+05:30',TIMESTAMPTZ '2026-09-16 09:30:00+05:30',NULL),
  ('ntf_risk_high','cmp_main','Officer123@gmail.com','IN_APP','3 students flagged high placement risk',
   'Amit, Aman and Vikram are flagged HIGH risk this cycle. Review recommended interventions.',
   'SENT',1,TIMESTAMPTZ '2026-09-15 10:00:00+05:30',TIMESTAMPTZ '2026-09-15 10:00:00+05:30',NULL),
  ('ntf_offer_priya','cmp_main','Officer123@gmail.com','IN_APP','Offer accepted: Priya (TechNova PPO)',
   'Priya Sharma accepted the TechNova pre-placement offer and is marked as joined.',
   'SENT',1,TIMESTAMPTZ '2026-09-20 14:15:00+05:30',TIMESTAMPTZ '2026-09-20 14:15:00+05:30',TIMESTAMPTZ '2026-09-20 15:00:00+05:30'),
  ('ntf_pending_digest','cmp_main','Officer123@gmail.com','EMAIL','Weekly placement digest',
   'Active drives: 12. Applications in funnel: 26. Offers extended: 7. See dashboard for details.',
   'PENDING',0,TIMESTAMPTZ '2026-09-22 07:00:00+05:30',NULL,NULL)
ON CONFLICT (id) DO UPDATE SET
  campus_id = EXCLUDED.campus_id, recipient_username = EXCLUDED.recipient_username, channel = EXCLUDED.channel,
  subject = EXCLUDED.subject, body = EXCLUDED.body, status = EXCLUDED.status,
  attempt_count = EXCLUDED.attempt_count, created_at = EXCLUDED.created_at,
  last_attempt_at = EXCLUDED.last_attempt_at, read_at = EXCLUDED.read_at;
