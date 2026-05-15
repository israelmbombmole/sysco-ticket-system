package com.sysco.web.service.courier;

import com.sysco.web.domain.CourierPacket;
import com.sysco.web.domain.CourierPacketExtraDirection;
import com.sysco.web.domain.UserAccount;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA visibility specification aligned with {@code CourierPacketDAO#listForScope} — uses
 * {@link com.sysco.web.security.RoleKeys#listRoleKey} for the role argument (same as desktop lists).
 */
public final class CourierPacketScope {

    private CourierPacketScope() {}

    public static Specification<CourierPacket> forUser(UserAccount ua, String scopeRole) {
        return (root, query, cb) -> scopePredicate(root, query, cb, ua, scopeRole);
    }

    private static Predicate scopePredicate(
            Root<CourierPacket> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            UserAccount ua,
            String rk) {

        if ("ADMIN".equals(rk) || "SUPER_ADMIN".equals(rk)) {
            return cb.conjunction();
        }
        if ("COURIER".equals(rk)) {
            if (ua.getId() == null) {
                return cb.disjunction();
            }
            return cb.equal(root.get("createdBy"), ua.getId());
        }
        if ("SECRETAIRE".equals(rk) || "DIRECTEUR".equals(rk) || "INSPECTEUR".equals(rk)) {
            if (ua.getDirectionId() == null) {
                return cb.disjunction();
            }
            Long dirId = ua.getDirectionId();
            Subquery<Long> sq = query.subquery(Long.class);
            Root<CourierPacketExtraDirection> ex = sq.from(CourierPacketExtraDirection.class);
            sq.select(ex.get("packetId"));
            sq.where(cb.equal(ex.get("directionId"), dirId));
            return cb.or(cb.equal(root.get("targetDirectionId"), dirId), root.get("id").in(sq));
        }
        if ("SOUS-DIRECTEUR".equals(rk)) {
            if (ua.getSousDirectionId() == null) {
                return cb.disjunction();
            }
            return cb.equal(root.get("targetSousDirectionId"), ua.getSousDirectionId());
        }
        if ("CONTROLEUR".equals(rk) || "VERIFICATEUR".equals(rk) || "VERIFICATEUR-ASSISTANT".equals(rk)) {
            if (ua.getSousDirectionId() != null) {
                return cb.equal(root.get("targetSousDirectionId"), ua.getSousDirectionId());
            }
            if (ua.getDirectionId() != null) {
                Long dirId = ua.getDirectionId();
                Subquery<Long> sq = query.subquery(Long.class);
                Root<CourierPacketExtraDirection> ex = sq.from(CourierPacketExtraDirection.class);
                sq.select(ex.get("packetId"));
                sq.where(cb.equal(ex.get("directionId"), dirId));
                return cb.or(cb.equal(root.get("targetDirectionId"), dirId), root.get("id").in(sq));
            }
            return cb.disjunction();
        }
        return cb.disjunction();
    }
}
