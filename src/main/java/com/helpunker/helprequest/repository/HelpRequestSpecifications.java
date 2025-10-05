package com.helpunker.helprequest.repository;

import com.helpunker.helprequest.domain.HelpRequest;
import com.helpunker.helprequest.domain.RequestStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class HelpRequestSpecifications {

    private HelpRequestSpecifications() {
    }

    public static Specification<HelpRequest> hasStatus(RequestStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<HelpRequest> ownedBy(UUID elderlyId) {
        return (root, query, cb) -> elderlyId == null ? null : cb.equal(root.get("elderly").get("id"), elderlyId);
    }

    public static Specification<HelpRequest> nearLocation(Double lat, Double lng, Double radiusKm) {
        if (lat == null || lng == null) {
            return null;
        }
        double effectiveRadius = radiusKm == null || radiusKm <= 0 ? 3.0 : radiusKm;
        double latDegree = effectiveRadius / 111.0d;
        double cosLat = Math.cos(Math.toRadians(lat));
        double lngDegree = effectiveRadius / (111.321d * Math.max(1.0e-6d, Math.abs(cosLat)));

        BigDecimal latMin = BigDecimal.valueOf(lat - latDegree);
        BigDecimal latMax = BigDecimal.valueOf(lat + latDegree);
        BigDecimal lngMin = BigDecimal.valueOf(lng - lngDegree);
        BigDecimal lngMax = BigDecimal.valueOf(lng + lngDegree);

        return (root, query, cb) -> cb.and(
                cb.isNotNull(root.get("locationLat")),
                cb.isNotNull(root.get("locationLng")),
                cb.between(root.get("locationLat"), latMin, latMax),
                cb.between(root.get("locationLng"), lngMin, lngMax));
    }
}
