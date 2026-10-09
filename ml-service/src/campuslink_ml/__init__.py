"""CampusLink ML inference service.

Provides versioned, explainable predictions (readiness, risk, ...) for the Spring Boot
system of record. Kept as a separate package from the OR-Tools scheduling worker per the
service specification: scheduling correctness and predictive recommendations have
different failure modes and release policies.
"""
