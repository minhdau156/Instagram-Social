package com.instagram.adapter.in.web.dto.request;

import java.util.List;
import java.util.UUID;

public record AddGroupMemberRequest(
        List<UUID> memberIds) {

}
