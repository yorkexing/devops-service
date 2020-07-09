import React, { useEffect } from 'react';
import './index.less';
import PropTypes from 'prop-types';
import { Icon } from 'choerodon-ui';
import StatusTag from '../StatusTag';

const detailHeader = ({ gitlabPipelineId, status, triggerRef, appServiceName, aHref, mainStore, appServiceId, projectId }) => {
  async function linkToGitlab() {
    try {
      await mainStore.checkLinkToGitlab(projectId, appServiceId);
      window.open(`${aHref}/commits/${triggerRef}`);
    } catch (e) {
      // return;
    }
  }

  return (
    <div className="c7ncd-pipelineManage-optsDetail-header">
      <span>#{gitlabPipelineId}</span>
      <span>
        (<Icon type="widgets_line" /><span>{appServiceName}</span>  -
        <Icon type="branch" />
        <span
          onClick={linkToGitlab}
          className="c7ncd-pipelineManage-optsDetail-header-ref"
        >
          {triggerRef}
        </span>)
      </span>
      <StatusTag status={status} size={12} />
    </div>
  );
};

detailHeader.propTypes = {
  gitlabPipelineId: PropTypes.number.isRequired,
  status: PropTypes.string.isRequired,
};

export default detailHeader;
