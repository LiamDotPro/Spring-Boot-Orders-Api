package com.liamread.orders.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared state for every persisted entity: identity, audit timestamps and an optimistic lock.
 *
 * <p>A {@code @MappedSuperclass} is not a table — its columns are copied into each subclass's own
 * table. That is deliberately different from {@code @Inheritance}, which would build one shared
 * table (or a join per read) across unrelated entities.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    /**
     * Time-ordered UUID rather than a random v4: random ids scatter inserts across the whole
     * primary-key btree, a time-ordered one appends like a sequence would.
     *
     * <p>No {@code @GeneratedValue} — Hibernate 6+ generator annotations are self-sufficient.
     */
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Populated by {@link AuditingEntityListener}, which needs {@code @EnableJpaAuditing}. */
    @CreatedDate
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic locking: Hibernate appends {@code WHERE version = ?} to updates and fails the
     * transaction if another writer got there first.
     */
    @Version
    private long version;

    /**
     * Identity-based, and final so a subclass cannot reintroduce the usual bug.
     *
     * <p>Never let Lombok generate these on an entity — {@code @Data}/{@code @EqualsAndHashCode}
     * use every field, so {@code equals} force-loads lazy collections and the hash changes the
     * moment {@code save()} assigns an id, corrupting any {@code HashSet} already holding it.
     *
     * <p>{@link Hibernate#getClass} unwraps proxies, so a lazy proxy compares equal to the
     * entity it stands for.
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        return id != null && id.equals(((BaseEntity) o).id);
    }

    /** Constant by design: it must not change across the transient → persistent transition. */
    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}
