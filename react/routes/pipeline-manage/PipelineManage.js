import React, { useRef } from 'react';
import { observer } from 'mobx-react-lite';
import { Page, Header, Breadcrumb, Content } from '@choerodon/boot';
import { Button } from 'choerodon-ui/pro';
import PipelineTree from './components/PipelineTree';
import PipelineFlow from './components/PipelineFlow';
import DragBar from '../../components/drag-bar';
import { usePipelineManageStore } from './stores';

import './index.less';

const PipelineManage = observer((props) => {
  const {
    prefixCls,
    mainStore,
  } = usePipelineManageStore();
  const rootRef = useRef(null);

  return (
    <Page className="pipelineManage_page">
      <Header title="流水线">
        <Button icon="playlist_add">创建流水线</Button>
        <Button icon="playlist_add">流水线记录详情</Button>
        <Button icon="playlist_add">强制失败</Button>
        <Button icon="playlist_add">刷新</Button>
      </Header>
      <Breadcrumb />
      <Content className={`${prefixCls}-content`}>
        <div
          ref={rootRef}
          className={`${prefixCls}-wrap`}
        >
          <DragBar
            parentRef={rootRef}
            store={mainStore}
          />
          <PipelineTree />
          <div className={`${prefixCls}-main ${prefixCls}-animate`}>
            <PipelineFlow />
          </div>
        </div>
      </Content>
    </Page>
  );
});

export default PipelineManage;
