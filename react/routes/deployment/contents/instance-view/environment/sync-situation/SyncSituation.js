import React, { Fragment, useContext, useMemo } from 'react';
import { FormattedMessage } from 'react-intl';
import { Permission } from '@choerodon/boot';
import { observer } from 'mobx-react-lite';
import {
  Tooltip,
  Button,
  Icon,
  Modal,
  DataSet,
} from 'choerodon-ui/pro';
import { Popover } from 'choerodon-ui';
import Store from '../../../../stores';
import GitopsLogDataSet from './stores/GitopsLogDataSet';
import RetryDataSet from './stores/RetryDataSet';

const SyncSituation = observer(({ refresh }) => {
  const {
    selectedMenu: { menuId },
    intl: { formatMessage },
    prefixCls,
    intlPrefix,
    AppState: { currentMenuType: { id } },
  } = useContext(Store);
  const dataSet = useMemo(() => new DataSet(GitopsLogDataSet(id, menuId)), [id, menuId]);
  const retryDataSet = useMemo(() => new DataSet(RetryDataSet(id, menuId)), [id, menuId]);

  const content = useMemo(() => (
    <Fragment>
      <p className="log-help-desc">
        <FormattedMessage id={`${intlPrefix}.environment.help`} />
      </p>
      <h4 className="log-help-title">
        <FormattedMessage id={`${intlPrefix}.environment.config`} />
      </h4>
      <p className="log-help-desc">
        <FormattedMessage id={`${intlPrefix}.environment.config.des`} />
      </p>
      <h4 className="log-help-title">
        <FormattedMessage id={`${intlPrefix}.environment.parsed`} />
      </h4>
      <p className="log-help-desc">
        <FormattedMessage id={`${intlPrefix}.environment.parsed.des`} />
      </p>
      <h4 className="log-help-title">
        <FormattedMessage id={`${intlPrefix}.environment.executed`} />
      </h4>
      <p className="log-help-desc">
        <FormattedMessage id={`${intlPrefix}.environment.executed.des`} />
      </p>
    </Fragment>
  ));

  /**
   * 打开重试弹窗
   */
  function showRetry() {
    Modal.open({
      key: 'retry',
      title: formatMessage({ id: `${intlPrefix}.environment.retry` }),
      children: <span>{formatMessage({ id: `${intlPrefix}.environment.retry.des` })}</span>,
      onOk: handleRetry,
    });
  }

  /**
   * 重试gitOps
   */
  async function handleRetry() {
    try {
      if ((await retryDataSet.query()) !== false) {
        refresh();
        dataSet.query();
      } else {
        return false;
      }
    } catch (e) {
      return false;
    }
  }

  const getDetail = useMemo(() => {
    const data = dataSet.data;
    if (data.length) {
      const record = data[0];
      const commitUrl = record.get('commitUrl');
      const sagaSyncCommit = record.get('sagaSyncCommit');
      const devopsSyncCommit = record.get('devopsSyncCommit');
      const agentSyncCommit = record.get('agentSyncCommit');

      return (
        <div className="log-sync-line">
          <div className="log-sync-line-card">
            <div className="log-sync-line-card-title">
              <FormattedMessage id={`${intlPrefix}.environment.gitlab`} />
            </div>
            <div className="log-sync-line-card-commit">
              <a
                href={`${commitUrl}${sagaSyncCommit}`}
                target="_blank"
                rel="nofollow me noopener noreferrer"
              >
                {sagaSyncCommit ? sagaSyncCommit.slice(0, 8) : null}
              </a>
            </div>
          </div>
          <div className="log-sync-line-arrow log-sync-line-retry">
            <Permission
              service={['devops-service.devops-environment.retryByGitOps']}
            >
              <Tooltip title={<FormattedMessage id={`${intlPrefix}.environment.retry`} />}>
                <Button
                  icon="replay"
                  color="blue"
                  funcType="flat"
                  onClick={showRetry}
                />
              </Tooltip>
            </Permission>
            <div className="c7n-log-arrow-detail">→</div>
          </div>
          <div className="log-sync-line-card">
            <div className="log-sync-line-card-title">
              <FormattedMessage id={`${intlPrefix}.environment.analysis`} />
            </div>
            <div className="log-sync-line-card-commit">
              <a
                href={`${commitUrl}${devopsSyncCommit}`}
                target="_blank"
                rel="nofollow me noopener noreferrer"
              >
                {devopsSyncCommit ? devopsSyncCommit.slice(0, 8) : null}
              </a>
            </div>
          </div>
          <div className="log-sync-line-arrow">
            <div className="c7n-log-arrow-detail">→</div>
          </div>
          <div className="log-sync-line-card">
            <div className="log-sync-line-card-title">
              <FormattedMessage id={`${intlPrefix}.environment.agent`} />
            </div>
            <div className="log-sync-line-card-commit">
              <a
                href={`${commitUrl}${agentSyncCommit}`}
                target="_blank"
                rel="nofollow me noopener noreferrer"
              >
                {agentSyncCommit ? agentSyncCommit.slice(0, 8) : null}
              </a>
            </div>
          </div>
        </div>
      );
    }
    return null;
  });

  return (
    <div className={`${prefixCls}-environment-sync-detail`}>
      <div className="log-sync-title">
        <span className="log-sync-title-text">
          {formatMessage({ id: `${intlPrefix}.environment.tabs.sync` })}
        </span>
        <Popover
          overlayClassName={`${prefixCls}-environment-sync-help`}
          placement="topLeft"
          content={content}
          arrowPointAtCenter
          theme="light"
        >
          <Icon type="help" className="log-sync-title-icon" />
        </Popover>
      </div>
      {getDetail}
    </div>
  );
});

export default SyncSituation;
