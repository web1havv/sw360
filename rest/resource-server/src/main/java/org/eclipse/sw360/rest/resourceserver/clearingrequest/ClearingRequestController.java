/*
 * Copyright Siemens AG, 2021. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.rest.resourceserver.clearingrequest;

import static org.eclipse.sw360.datahandler.common.WrappedException.wrapTException;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.apache.thrift.TException;
import org.eclipse.sw360.datahandler.common.SW360Constants;
import org.eclipse.sw360.datahandler.resourcelists.PaginationParameterException;
import org.eclipse.sw360.datahandler.resourcelists.PaginationResult;
import org.eclipse.sw360.datahandler.resourcelists.ResourceClassNotFoundException;
import org.eclipse.sw360.datahandler.thrift.ClearingRequestState;
import org.eclipse.sw360.datahandler.thrift.Comment;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.projects.ClearingRequest;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.rest.resourceserver.core.HalResource;
import org.eclipse.sw360.rest.resourceserver.core.RestControllerHelper;
import org.eclipse.sw360.rest.resourceserver.project.Sw360ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.BasePathAwareController;
import org.springframework.data.rest.webmvc.RepositoryLinksResource;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelProcessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@BasePathAwareController
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@RestController
@SecurityRequirement(name = "tokenAuth")
@SecurityRequirement(name = "basic")
public class ClearingRequestController implements RepresentationModelProcessor<RepositoryLinksResource> {

    public static final String CLEARING_REQUEST_URL = "/clearingrequest";

    public static final String CLEARING_REQUESTS_URL = "/clearingrequests";

    @Autowired
    private Sw360ClearingRequestService sw360ClearingRequestService;

    @NonNull
    private final RestControllerHelper restControllerHelper;

    @NonNull
    private final Sw360ProjectService projectService;

    @NonNull
    private final com.fasterxml.jackson.databind.Module sw360Module;


    @Operation(
            summary = "Get clearing request by id.",
            description = "Get a clearing request by id.",
            tags = {"ClearingRequest"}
    )
    @RequestMapping(value = CLEARING_REQUEST_URL + "/{id}", method = RequestMethod.GET)
    public ResponseEntity<EntityModel<ClearingRequest>> getClearingRequestById(
            Pageable pageable,
            @Parameter(description = "id of the clearing request")
            @PathVariable("id") String docId,
            HttpServletRequest request
    ) throws TException, URISyntaxException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        ClearingRequest clearingRequest = sw360ClearingRequestService.getClearingRequestById(docId, sw360User);
        HalResource<ClearingRequest> halClearingRequest = createHalClearingRequestWithAllDetails(clearingRequest, sw360User, true);
        HttpStatus status = halClearingRequest == null ? HttpStatus.NO_CONTENT : HttpStatus.OK;
        return new ResponseEntity<>(halClearingRequest, status);
    }

    @Operation(
            summary = "Get the ClearingRequest based on the project id.",
            description = "Get the ClearingRequest based on the project id.",
            tags = {"ClearingRequest"}
    )
    @RequestMapping(value = CLEARING_REQUEST_URL + "/project/{id}", method = RequestMethod.GET)
    public ResponseEntity<EntityModel<ClearingRequest>> getClearingRequestByProjectId(
            Pageable pageable,
            @Parameter(description = "id of the project")
            @PathVariable("id") String projectId,
            HttpServletRequest request
    ) throws TException, URISyntaxException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        ClearingRequest clearingRequest = sw360ClearingRequestService.getClearingRequestByProjectId(projectId, sw360User);
        HalResource<ClearingRequest> halClearingRequest = createHalClearingRequestWithAllDetails(clearingRequest, sw360User, true);
        HttpStatus status = halClearingRequest == null ? HttpStatus.NO_CONTENT : HttpStatus.OK;
        return new ResponseEntity<>(halClearingRequest, status);
    }

    private HalResource<ClearingRequest> createHalClearingRequestWithAllDetails(ClearingRequest clearingRequest, User sw360User, boolean isSingleRequest) throws TException {
        HalResource<ClearingRequest> halClearingRequest = new HalResource<>(clearingRequest);
        if (StringUtils.hasText(clearingRequest.projectId)) {
            Project project = projectService.getProjectForUserById(clearingRequest.getProjectId(), sw360User);
            Project projectWithClearingInfo = projectService.getClearingInfo(project, sw360User);
            restControllerHelper.addEmbeddedReleaseDetails(halClearingRequest, projectWithClearingInfo);
            restControllerHelper.addEmbeddedProject(halClearingRequest, project, true);
        }
        User requestingUser = restControllerHelper.getUserByEmail(clearingRequest.getRequestingUser());
        restControllerHelper.addEmbeddedUser(halClearingRequest, requestingUser, "requestingUser");
        if(isSingleRequest){
            User clearingTeam = restControllerHelper.getUserByEmail(clearingRequest.getClearingTeam());
            restControllerHelper.addEmbeddedUser(halClearingRequest, clearingTeam, "clearingTeam");
        }
        if(clearingRequest.getClearingState().equals(ClearingRequestState.CLOSED) || clearingRequest.getClearingState().equals(ClearingRequestState.REJECTED)){
            restControllerHelper.addEmbeddedTimestampOfDecision(halClearingRequest,clearingRequest.getTimestampOfDecision());
        }
        restControllerHelper.addEmbeddedDatesClearingRequest(halClearingRequest, clearingRequest, isSingleRequest);
        return halClearingRequest;
    }

    @Operation(
            summary = "Get all the Clearing Requests visible to the user.",
            description = "Get all the Clearing Requests visible to the user.",
            tags = {"ClearingRequest"}
    )
    @RequestMapping(value = CLEARING_REQUESTS_URL, method = RequestMethod.GET)
    public ResponseEntity<CollectionModel<EntityModel<ClearingRequest>>> getMyClearingRequests(
            Pageable pageable,
            @Parameter(description = "The clearing request state of the request.",
                    schema = @Schema(
                            implementation = ClearingRequestState.class
                    )
            )
            @RequestParam(value = "state", required = false) String state,
            HttpServletRequest request
    ) throws TException {

        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        Set<ClearingRequest> clearingRequestSet = new TreeSet<>();
        ClearingRequestState crState = null;
        if (StringUtils.hasText(state)) {
            try {
                crState = ClearingRequestState.valueOf(state.toUpperCase());
            } catch (IllegalArgumentException exp) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.format("Invalid ClearingRequest state '%s', possible values are: %s", state, Arrays.asList(ClearingRequestState.values())));
            }
        }
            clearingRequestSet.addAll(sw360ClearingRequestService.getMyClearingRequests(sw360User,crState));

        List<EntityModel<ClearingRequest>> clearingRequestList = new ArrayList<>();
        for (ClearingRequest cr : clearingRequestSet) {
            ClearingRequest embeddedCR = restControllerHelper.convertToEmbeddedClearingRequest(cr);
            HalResource<ClearingRequest> halResource = createHalClearingRequestWithAllDetails(embeddedCR, sw360User, false);
            clearingRequestList.add(halResource);
        }

        CollectionModel<EntityModel<ClearingRequest>> resources = CollectionModel.of(clearingRequestList);
        HttpStatus status = resources == null ? HttpStatus.NO_CONTENT : HttpStatus.OK;
        return new ResponseEntity<>(resources, status);
    }


    @Operation(
            summary = "Get comments for a specific clearing request",
            description = "Fetch a paginated list of comments associated with the given clearing request ID.",
            tags = {"ClearingRequest"}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the comments",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Comment.class))),
            @ApiResponse(responseCode = "404", description = "Clearing request not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping(value = CLEARING_REQUEST_URL + "/{id}/comments")
    public ResponseEntity<CollectionModel<?>> getCommentsByClearingRequestId(
            @PathVariable("id") String crId,
            HttpServletRequest request,
            Pageable pageable) throws TException, URISyntaxException {
        try {
            User sw360User = restControllerHelper.getSw360UserFromAuthentication();
            ClearingRequest clearingRequest = sw360ClearingRequestService.getClearingRequestById(crId, sw360User);

            List<Comment> commentList = clearingRequest.getComments().stream().sorted((c1, c2) -> Long.compare(c2.getCommentedOn(), c1.getCommentedOn()))
                    .collect(Collectors.toList());
            PaginationResult<Comment> paginationResult = restControllerHelper.createPaginationResult(request, pageable, commentList, SW360Constants.TYPE_COMMENT);
            final List<EntityModel<Comment>> commentResources = new ArrayList<>();
            for (Comment comment : paginationResult.getResources()) {
                Comment embeddedComment = restControllerHelper.convertToEmbeddedComment(comment);
                HalResource<Comment> commentHalResource = createHalComment(embeddedComment);
                commentResources.add(commentHalResource);
            }
            CollectionModel<EntityModel<Comment>> resources;
            if (commentResources.isEmpty()) {
                resources = restControllerHelper.emptyPageResource(Comment.class, paginationResult);
            } else {
                resources = restControllerHelper.generatePagesResource(paginationResult, commentResources);
            }
            HttpStatus status = resources == null ? HttpStatus.NO_CONTENT : HttpStatus.OK;
            return new ResponseEntity<>(resources, status);
        } catch (Exception e) {
            throw new TException(e.getMessage());
        }
    }

    private HalResource<Comment> createHalComment(Comment comment) throws TException {
        HalResource<Comment> halComment = new HalResource<>(comment);
        User commentinguser = restControllerHelper.getUserByEmail(comment.getCommentedBy());
        restControllerHelper.addEmbeddedUser(halComment, commentinguser, "commentingUser");
        return halComment;
    }

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(linkTo(ClearingRequestController.class).slash("api" + CLEARING_REQUEST_URL).withRel("clearingRequests"));
        return resource;
    }
}
