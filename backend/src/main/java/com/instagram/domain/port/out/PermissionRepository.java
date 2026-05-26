package com.instagram.domain.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.instagram.domain.model.Permission;
import com.instagram.domain.model.PermissionName;

public interface PermissionRepository {
    List<Permission> findAll();

    Optional<Permission> findByName(PermissionName name);

    Set<Permission> findByIds(Collection<UUID> ids);
}
