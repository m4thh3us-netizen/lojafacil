package com.lojafacil.auth;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lojafacil.auth.AuthDtos.InvitationView;
import com.lojafacil.auth.AuthDtos.InviteRequest;
import com.lojafacil.auth.AuthDtos.UpdateMemberRequest;
import com.lojafacil.auth.AuthDtos.UserView;
import com.lojafacil.shared.security.Access;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/admin/team")
@PreAuthorize(Access.OWNER)
@Tag(name = "Equipe")
public class TeamController {
    private final TeamService team;

    public TeamController(TeamService team) {
        this.team = team;
    }

    @GetMapping
    public TeamService.TeamView list() {
        return team.list();
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationView invite(@Valid @RequestBody InviteRequest req) {
        return team.invite(req);
    }

    @DeleteMapping("/invitations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long id) {
        team.revoke(id);
    }

    @PutMapping("/members/{id}")
    public UserView updateMember(@PathVariable Long id, @Valid @RequestBody UpdateMemberRequest req) {
        return team.updateMember(id, req);
    }
}
