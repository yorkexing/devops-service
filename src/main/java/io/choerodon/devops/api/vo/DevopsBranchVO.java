package io.choerodon.devops.api.vo;

import java.util.List;

import io.choerodon.devops.infra.dto.DevopsGitlabCommitDTO;

public class DevopsBranchVO {

    private Long appServiceId;
    private String appServiceName;
    private String originBranch;
    private Long issueId;
    private String branchName;
    private List<DevopsGitlabCommitDTO> commits;
    private List<CustomMergeRequestVO> mergeRequests;

    public String getOriginBranch() {
        return originBranch;
    }

    public void setOriginBranch(String originBranch) {
        this.originBranch = originBranch;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public Long getIssueId() {
        return issueId;
    }

    public void setIssueId(Long issueId) {
        this.issueId = issueId;
    }

    public Long getAppServiceId() {
        return appServiceId;
    }

    public void setAppServiceId(Long appServiceId) {
        this.appServiceId = appServiceId;
    }

    public String getAppServiceName() {
        return appServiceName;
    }

    public void setAppServiceName(String appServiceName) {
        this.appServiceName = appServiceName;
    }

    public List<DevopsGitlabCommitDTO> getCommits() {
        return commits;
    }

    public void setCommits(List<DevopsGitlabCommitDTO> commits) {
        this.commits = commits;
    }

    public List<CustomMergeRequestVO> getMergeRequests() {
        return mergeRequests;
    }

    public void setMergeRequests(List<CustomMergeRequestVO> mergeRequests) {
        this.mergeRequests = mergeRequests;
    }
}
