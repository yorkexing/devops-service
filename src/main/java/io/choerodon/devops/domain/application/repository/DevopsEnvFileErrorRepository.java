package io.choerodon.devops.domain.application.repository;

import java.util.List;

import io.choerodon.core.domain.Page;
import io.choerodon.devops.domain.application.entity.DevopsEnvFileErrorE;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;

/**
 * Creator: Runge
 * Date: 2018/8/9
 * Time: 20:44
 * Description:
 */
public interface DevopsEnvFileErrorRepository {

    DevopsEnvFileErrorE create(DevopsEnvFileErrorE DevopsEnvFileErrorE);

    List<DevopsEnvFileErrorE> listByEnvId(Long envId);

    Page<DevopsEnvFileErrorE> pageByEnvId(Long envId, PageRequest pageRequest);

    void delete(DevopsEnvFileErrorE DevopsEnvFileErrorE);

}
