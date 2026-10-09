package com.nexploy.campuslink.student.service;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * The curated, deterministic source of roadmap <em>structure</em>: for each well-known role family, an
 * ordered list of phases, each with ordered tasks tagged by the skill they build. This is
 * version-controlled, human-authored content — never AI-invented — so the roadmap a student sees is
 * transparent and reproducible (same target role -> same phases/tasks).
 *
 * <p>The skill labels here deliberately share the vocabulary used by {@link ResumeAnalyzer}'s
 * role-keyword families, so a skill the student has already demonstrated maps 1:1 onto the catalog
 * task that builds it — letting {@link RoadmapService} pre-complete it ("don't learn Java when the
 * resume already shows Java"). Ordering encodes prerequisites (foundations before scale/ship), the
 * only priority signal used; there is no arbitrary AI ranking.
 */
@Component
public class RoadmapCatalog {

    /** One learning step. {@code skill} matches the demonstrated-skill vocabulary for auto-completion. */
    public record TaskTemplate(String title, String detail, String skill) {}

    /** An ordered group of tasks. {@code rationale} explains why this phase comes where it does. */
    public record PhaseTemplate(String key, String title, String rationale, List<TaskTemplate> tasks) {}

    /**
     * The ordered phases for a target role. Falls back to a broadly-relevant engineering track for an
     * unknown/blank role, mirroring {@link ResumeAnalyzer}'s keyword-family fallback. Callers that want
     * to substitute officer-managed drive requirements for that generic fallback should use
     * {@link #curatedPhasesFor} and check for null instead.
     */
    public List<PhaseTemplate> phasesFor(String targetRole) {
        List<PhaseTemplate> curated = curatedPhasesFor(targetRole);
        return curated != null ? curated : generic();
    }

    /**
     * The curated track when the target role matches a known role family, or null when no family
     * matches (unknown/blank role). The null is the signal {@link RoadmapService} uses to auto-build
     * phases from officer-managed drive skill requirements before falling back to the generic track.
     */
    public List<PhaseTemplate> curatedPhasesFor(String targetRole) {
        String role = targetRole == null ? "" : targetRole.toLowerCase(Locale.ROOT);
        // "database" also contains "data" — match the more specific family first so a
        // Database Engineer gets the SQL/DB track, not the data-science one.
        if (role.contains("database") || role.contains("dba")) {
            return database();
        } else if (role.contains("data") || role.contains("ml") || role.contains("machine")) {
            return dataScience();
        } else if (role.contains("devops") || role.contains("sre") || role.contains("infra")) {
            return devops();
        } else if (role.contains("front")) {
            return frontend();
        } else if (role.contains("full")) {
            return fullStack();
        } else if (role.contains("back") || role.contains("java") || role.contains("server")) {
            return backend();
        } else if (role.contains("mobile") || role.contains("android") || role.contains("ios")) {
            return mobile();
        } else if (role.contains("civil") || role.contains("structural") || role.contains("construction")) {
            return civil();
        } else if (role.contains("electrical") || role.contains("circuit") || role.contains("power")) {
            return electrical();
        } else if (role.contains("mechanical") || role.contains("thermal") || role.contains("manufacturing")) {
            return mechanical();
        }
        return null;
    }

    private List<PhaseTemplate> backend() {
        return List.of(
            new PhaseTemplate("apis", "Build REST APIs",
                "APIs are the core of backend work — start here before scaling or deploying.",
                List.of(
                    new TaskTemplate("Design and build a REST API",
                        "Model resources and expose CRUD endpoints with proper status codes.", "REST API"),
                    new TaskTemplate("Persist data with SQL",
                        "Design a relational schema and query it from your API.", "SQL"))),
            new PhaseTemplate("scale", "Scale and design for growth",
                "Once you can build one service, learn to split and design systems that scale.",
                List.of(
                    new TaskTemplate("Split a service into microservices",
                        "Decompose a monolith into independently deployable services.", "Microservices"),
                    new TaskTemplate("Practise system design",
                        "Sketch scalable designs (caching, queues, load balancing) for common systems.", "System Design"))),
            new PhaseTemplate("ship", "Containerize and deploy",
                "Shipping and operating software is expected of backend engineers — do it last, on top of a working service.",
                List.of(
                    new TaskTemplate("Containerize a service with Docker",
                        "Write a Dockerfile and run your service in a container.", "Docker"))));
    }

    private List<PhaseTemplate> fullStack() {
        return List.of(
            new PhaseTemplate("frontend", "Build the front end",
                "Full-stack starts with a UI users can interact with.",
                List.of(
                    new TaskTemplate("Build a React front end",
                        "Create a component-driven UI that consumes an API.", "React"),
                    new TaskTemplate("Add TypeScript",
                        "Type your components and API calls for safer refactors.", "TypeScript"))),
            new PhaseTemplate("backend", "Build the back end",
                "Connect the UI to a real API and database.",
                List.of(
                    new TaskTemplate("Build a REST API",
                        "Expose endpoints the front end consumes.", "REST API"),
                    new TaskTemplate("Persist data with SQL",
                        "Design and query a relational database.", "SQL"))),
            new PhaseTemplate("ship", "Containerize and deploy",
                "Deploy the full stack once both halves work.",
                List.of(
                    new TaskTemplate("Containerize with Docker",
                        "Package the app for reproducible deployment.", "Docker"))));
    }

    private List<PhaseTemplate> frontend() {
        return List.of(
            new PhaseTemplate("core", "Master the UI core",
                "Component fundamentals and typing come before polish.",
                List.of(
                    new TaskTemplate("Build with React",
                        "Create reusable, state-driven components.", "React"),
                    new TaskTemplate("Add TypeScript",
                        "Type props and state to catch errors early.", "TypeScript"))),
            new PhaseTemplate("craft", "Style and accessibility",
                "A front-end engineer ships accessible, well-styled UI.",
                List.of(
                    new TaskTemplate("Layout and styling with CSS",
                        "Build responsive layouts with modern CSS.", "CSS"),
                    new TaskTemplate("Make it accessible",
                        "Apply semantic HTML and ARIA so the UI is usable by everyone.", "Accessibility"))),
            new PhaseTemplate("data", "Talk to APIs",
                "Wire the UI to real data.",
                List.of(
                    new TaskTemplate("Consume a REST API",
                        "Fetch, cache and render server data with loading/error states.", "REST"))));
    }

    private List<PhaseTemplate> database() {
        return List.of(
            new PhaseTemplate("sql", "Master SQL and schema design",
                "Deep SQL and sound schema design are the core of database work — start here.",
                List.of(
                    new TaskTemplate("Write advanced SQL queries",
                        "Joins, subqueries, window functions and aggregation on real datasets.", "SQL"),
                    new TaskTemplate("Design a relational schema",
                        "Normalise data, model relationships and enforce constraints.", "Database Design"))),
            new PhaseTemplate("performance", "Tune and optimise",
                "Once queries work, learn to keep them fast as data grows.",
                List.of(
                    new TaskTemplate("Index and optimise queries",
                        "Read query plans, add indexes and fix slow queries.", "Query Optimisation"),
                    new TaskTemplate("Practise data modelling",
                        "Model a real-world domain balancing reads, writes and integrity.", "Data Modelling"))),
            new PhaseTemplate("ops", "Operate and ship",
                "Database engineers also ship: containerise the data store and version-control changes.",
                List.of(
                    new TaskTemplate("Containerize a database with Docker",
                        "Run and configure a database inside a container.", "Docker"),
                    new TaskTemplate("Version-control schema changes with Git",
                        "Track migrations and collaborate with Git.", "Git"))));
    }

    private List<PhaseTemplate> dataScience() {
        return List.of(
            new PhaseTemplate("foundations", "Data foundations",
                "Programming and querying data come before modelling.",
                List.of(
                    new TaskTemplate("Python for data",
                        "Manipulate and analyse data in Python.", "Python"),
                    new TaskTemplate("Query data with SQL",
                        "Extract and aggregate data from relational sources.", "SQL"),
                    new TaskTemplate("Wrangle data with Pandas",
                        "Clean, join and reshape datasets with Pandas.", "Pandas"))),
            new PhaseTemplate("modelling", "Statistics and modelling",
                "Model only after you can prepare data and reason about it statistically.",
                List.of(
                    new TaskTemplate("Apply statistics",
                        "Use descriptive and inferential statistics to reason about data.", "Statistics"),
                    new TaskTemplate("Build a machine learning model",
                        "Train, evaluate and iterate on a supervised model.", "Machine Learning"))));
    }

    private List<PhaseTemplate> devops() {
        return List.of(
            new PhaseTemplate("containers", "Containers and CI/CD",
                "Reproducible builds and pipelines are the foundation of DevOps.",
                List.of(
                    new TaskTemplate("Containerize with Docker",
                        "Package applications into portable images.", "Docker"),
                    new TaskTemplate("Automate a CI/CD pipeline",
                        "Build, test and deploy automatically on every push.", "CI/CD"))),
            new PhaseTemplate("orchestration", "Orchestrate and provision",
                "Once containers ship, learn to run and provision them at scale.",
                List.of(
                    new TaskTemplate("Orchestrate with Kubernetes",
                        "Deploy and scale containers on a cluster.", "Kubernetes"),
                    new TaskTemplate("Deploy on the cloud",
                        "Provision and run infrastructure on AWS.", "AWS"),
                    new TaskTemplate("Provision with Terraform",
                        "Manage infrastructure as code.", "Terraform"))));
    }

    private List<PhaseTemplate> mobile() {
        return List.of(
            new PhaseTemplate("core", "Build a mobile app",
                "Start with the platform language and a working app.",
                List.of(
                    new TaskTemplate("Build with Kotlin",
                        "Build an Android app in Kotlin.", "Kotlin"),
                    new TaskTemplate("Build with Swift",
                        "Build an iOS app in Swift.", "Swift"))),
            new PhaseTemplate("data", "State and data",
                "A real app manages state and talks to a backend.",
                List.of(
                    new TaskTemplate("Manage app state",
                        "Apply a state-management pattern to keep UI consistent.", "State Management"),
                    new TaskTemplate("Consume a REST API",
                        "Fetch and cache server data in the app.", "REST API"))),
            new PhaseTemplate("quality", "Test your app",
                "Ship with confidence by testing critical flows.",
                List.of(
                    new TaskTemplate("Add automated tests",
                        "Cover core logic and UI flows with tests.", "Testing"))));
    }

    private List<PhaseTemplate> generic() {
        return List.of(
            new PhaseTemplate("foundations", "Engineering foundations",
                "Data structures and version control underpin every engineering role.",
                List.of(
                    new TaskTemplate("Practise data structures & algorithms",
                        "Solve problems across the core data structures.", "Data Structures"),
                    new TaskTemplate("Use Git confidently",
                        "Branch, merge and collaborate with Git.", "Git"))),
            new PhaseTemplate("building", "Build and persist",
                "Turn fundamentals into working software.",
                List.of(
                    new TaskTemplate("Build a REST API",
                        "Expose endpoints for a small application.", "REST API"),
                    new TaskTemplate("Persist data with SQL",
                        "Design and query a relational database.", "SQL"))),
            new PhaseTemplate("quality", "Test your work",
                "Testing signals engineering maturity to interviewers.",
                List.of(
                    new TaskTemplate("Add automated tests",
                        "Cover core logic with automated tests.", "Testing"))));
    }

    public List<PhaseTemplate> electrical() {
        return List.of(
            new PhaseTemplate("circuits", "Circuit Analysis & Simulation",
                "Master circuit theorems, network analysis, and simulation tools.",
                List.of(
                    new TaskTemplate("Master Circuit Analysis",
                        "Solve AC/DC circuit analysis, mesh and nodal analysis.", "Circuit Analysis"),
                    new TaskTemplate("Simulate with MATLAB / Simulink",
                        "Simulate electrical circuits and power loops in MATLAB.", "MATLAB"))),
            new PhaseTemplate("machines", "Power Systems & Machines",
                "Learn transformer design, electrical machines, and grid fundamentals.",
                List.of(
                    new TaskTemplate("Study Electrical Machines",
                        "Analyze DC/AC motors, synchronous generators, and transformers.", "Electrical Machines"),
                    new TaskTemplate("Power Electronics & Drives",
                        "Design inverters, converters, and motor speed control systems.", "Power Electronics"))),
            new PhaseTemplate("embedded", "Embedded & Control Systems",
                "Connect hardware with microcontrollers and control theory.",
                List.of(
                    new TaskTemplate("Program Microcontrollers",
                        "Interface sensors and actuators with Arduino or ARM Cortex.", "Microcontrollers"))));
    }

    public List<PhaseTemplate> civil() {
        return List.of(
            new PhaseTemplate("drafting", "Drafting & Structural Mechanics",
                "Foundational drafting and strength of materials required for site & design roles.",
                List.of(
                    new TaskTemplate("Draft with AutoCAD",
                        "Create 2D architectural and structural drawings in AutoCAD.", "AutoCAD"),
                    new TaskTemplate("Master Strength of Materials",
                        "Calculate bending moments, shear forces, and stress analysis.", "Structural Mechanics"))),
            new PhaseTemplate("design", "Structural Analysis & RCC Design",
                "Design reinforced concrete structures and modern analysis software.",
                List.of(
                    new TaskTemplate("Analyze in STAAD Pro / ETABS",
                        "Model and load-test multi-story buildings and frames.", "STAAD Pro"),
                    new TaskTemplate("Design Concrete Structures",
                        "Design beams, columns, and foundations as per IS codes.", "RCC Design"))),
            new PhaseTemplate("site", "Estimation & Construction Management",
                "Project scheduling, quantity surveying, and site execution practices.",
                List.of(
                    new TaskTemplate("Quantity Surveying & Estimation",
                        "Calculate bill of quantities (BOQ) and rate analysis.", "Estimation"))));
    }

    public List<PhaseTemplate> mechanical() {
        return List.of(
            new PhaseTemplate("cad", "CAD Modeling & Engineering Mechanics",
                "Parametric 3D modeling and foundational statics and dynamics.",
                List.of(
                    new TaskTemplate("3D Modeling in SolidWorks / CATIA",
                        "Create parametric 3D parts and assembly drawings.", "SolidWorks"),
                    new TaskTemplate("Engineering Mechanics & SOM",
                        "Analyze stress, strain, and material failure theories.", "Strength of Materials"))),
            new PhaseTemplate("thermal", "Thermodynamics & Fluid Systems",
                "Thermal systems, heat transfer, and fluid mechanics principles.",
                List.of(
                    new TaskTemplate("Apply Thermodynamics",
                        "Analyze refrigeration, IC engines, and power cycles.", "Thermodynamics"),
                    new TaskTemplate("FEA Simulation in ANSYS",
                        "Run structural and thermal finite element simulations.", "ANSYS"))),
            new PhaseTemplate("manufacturing", "Manufacturing & GD&T",
                "Understand CNC machining, tooling, and geometric dimensioning.",
                List.of(
                    new TaskTemplate("Master GD&T Standards",
                        "Interpret engineering drawings with geometric dimensioning & tolerancing.", "GD&T"))));
    }
}
