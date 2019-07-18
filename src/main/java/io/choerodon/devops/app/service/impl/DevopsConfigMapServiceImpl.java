package io.choerodon.devops.app.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.gson.Gson;
import io.choerodon.core.convertor.ConvertHelper;
import io.choerodon.core.convertor.ConvertPageHelper;
import io.choerodon.core.exception.CommonException;
import io.choerodon.devops.api.vo.DevopsConfigMapRepDTO;
import io.choerodon.devops.api.vo.DevopsConfigMapVO;
import io.choerodon.devops.app.service.DevopsConfigMapService;
import io.choerodon.devops.app.service.DevopsEnvironmentService;
import io.choerodon.devops.app.service.GitlabGroupMemberService;
import io.choerodon.devops.domain.application.repository.*;
import io.choerodon.devops.infra.dto.DevopsConfigMapDTO;
import io.choerodon.devops.infra.enums.CommandStatus;
import io.choerodon.devops.infra.enums.CommandType;
import io.choerodon.devops.infra.enums.ObjectType;
import io.choerodon.devops.infra.handler.ClusterConnectionHandler;
import io.choerodon.devops.infra.mapper.DevopsConfigMapMapper;
import io.choerodon.devops.infra.util.GitUserNameUtil;
import io.choerodon.devops.infra.util.PageRequestUtil;
import io.choerodon.devops.infra.util.TypeUtil;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ObjectMeta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DevopsConfigMapServiceImpl implements DevopsConfigMapService {

    public static final String CREATE_TYPE = "create";
    public static final String UPDATE_TYPE = "update";
    public static final String DELETE_TYPE = "delete";
    public static final String CONFIGMAP = "ConfigMap";
    public static final String CONFIG_MAP_PREFIX = "configMap-";
    private Gson gson = new Gson();


    @Autowired
    private ClusterConnectionHandler clusterConnectionHandler;
    @Autowired
    private UserAttrRepository userAttrRepository;
    @Autowired
    private GitlabGroupMemberService gitlabGroupMemberService;
    @Autowired
    private ResourceFileCheckHandler resourceFileCheckHandler;
    @Autowired
    private DevopsEnvironmentService devopsEnvironmentService;
    @Autowired
    private DevopsConfigMapMapper devopsConfigMapMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrUpdate(Long projectId, Boolean sync, DevopsConfigMapVO devopsConfigMapVO) {

        DevopsEnvironmentE devopsEnvironmentE = devopsEnvironmentRepository.baseQueryById(devopsConfigMapVO.getEnvId());

        UserAttrE userAttrE = null;
        if (!sync) {
            userAttrE = userAttrRepository.baseQueryById(TypeUtil.objToLong(GitUserNameUtil.getUserId()));
            //检验gitops库是否存在，校验操作人是否是有gitops库的权限
            gitlabGroupMemberService.checkEnvProject(devopsEnvironmentE, userAttrE);
        } else {
            userAttrE = new UserAttrE();
            userAttrE.setGitlabUserId(1L);
        }

        //校验用户是否有环境的权限
        if (!sync) {
            //校验环境相关信息
            devopsEnvironmentService.checkEnv(devopsEnvironmentE, userAttrE);
        }

        //初始化ConfigMap对象
        V1ConfigMap v1ConfigMap = initConfigMap(devopsConfigMapVO);

        //处理创建数据
        DevopsConfigMapE devopsConfigMapE = ConvertHelper.convert(devopsConfigMapVO, DevopsConfigMapE.class);
        devopsConfigMapE.setValue(gson.toJson(devopsConfigMapVO.getValue()));
        //更新判断configMap key-value是否改变
        if (devopsConfigMapVO.getType().equals(UPDATE_TYPE)) {

            //更新configMap的时候校验gitops库文件是否存在,处理部署configMap时，由于没有创gitops文件导致的部署失败
            resourceFileCheckHandler.check(devopsEnvironmentE, devopsConfigMapVO.getId(), devopsConfigMapVO.getName(), CONFIGMAP);

            if (devopsConfigMapVO.getValue().equals(gson.fromJson(devopsConfigMapRepository.baseQueryById(devopsConfigMapE.getId()).getValue(), Map.class))) {
                devopsConfigMapRepository.baseUpdate(devopsConfigMapE);
                return;
            }
        }
        DevopsEnvCommandVO devopsEnvCommandE = initDevopsEnvCommandE(devopsConfigMapVO.getType());

        //判断当前容器目录下是否存在环境对应的gitops文件目录，不存在则克隆
        String filePath = clusterConnectionHandler.handDevopsEnvGitRepository(devopsEnvironmentE.getProjectE().getId(), devopsEnvironmentE.getCode(), devopsEnvironmentE.getEnvIdRsa());


        //在gitops库处理ingress文件
        operateEnvGitLabFile(
                TypeUtil.objToInteger(devopsEnvironmentE.getGitlabEnvProjectId()), v1ConfigMap, devopsConfigMapVO.getType().equals(CREATE_TYPE), filePath, devopsConfigMapE, userAttrE, devopsEnvCommandE, devopsConfigMapVO.getAppId());
    }


    @Override
    public DevopsConfigMapRespVO createOrUpdateByGitOps(DevopsConfigMapVO devopsConfigMapVO, Long userId) {
        DevopsEnvironmentDTO environmentDTO = devopsEnvironmentService.baseQueryById(devopsConfigMapVO.getEnvId());
        //校验环境是否连接
        clusterConnectionHandler.checkEnvConnection(environmentDTO.getClusterId());

        //处理创建数据
        DevopsConfigMapE devopsConfigMapE = ConvertHelper.convert(devopsConfigMapVO, DevopsConfigMapE.class);
        devopsConfigMapE.setValue(gson.toJson(devopsConfigMapVO.getValue()));
        DevopsEnvCommandVO devopsEnvCommandE = initDevopsEnvCommandE(devopsConfigMapVO.getType());
        devopsEnvCommandE.setCreatedBy(userId);

        if (devopsConfigMapVO.getType().equals(CREATE_TYPE)) {
            Long configMapId = devopsConfigMapRepository.baseCreate(devopsConfigMapE).getId();
            devopsEnvCommandE.setObjectId(configMapId);
            devopsConfigMapE.setId(configMapId);
            devopsConfigMapE.initDevopsEnvCommandE(devopsEnvCommandRepository.create(devopsEnvCommandE).getId());
            devopsConfigMapRepository.baseUpdate(devopsConfigMapE);
        } else {
            devopsEnvCommandE.setObjectId(devopsConfigMapE.getId());
            devopsConfigMapE.initDevopsEnvCommandE(devopsEnvCommandRepository.create(devopsEnvCommandE).getId());
            devopsConfigMapRepository.baseUpdate(devopsConfigMapE);
        }
        return ConvertHelper.convert(devopsConfigMapE, DevopsConfigMapRepDTO.class);
    }

    @Override
    public DevopsConfigMapRepDTO query(Long configMapId) {
        DevopsConfigMapE devopsConfigMapE = devopsConfigMapRepository.baseQueryById(configMapId);
        DevopsConfigMapRepDTO devopsConfigMapRepDTO = ConvertHelper.convert(devopsConfigMapE, DevopsConfigMapRepDTO.class);
        devopsConfigMapRepDTO.setValue(gson.fromJson(devopsConfigMapE.getValue(), Map.class));
        return devopsConfigMapRepDTO;
    }

    @Override
    public PageInfo<DevopsConfigMapRepDTO> pageByOptions(Long projectId, Long envId, PageRequest pageRequest, String searchParam, Long appId) {
        PageInfo<DevopsConfigMapE> devopsConfigMapES = devopsConfigMapRepository.basePageByEnv(
                envId, pageRequest, searchParam,appId);
        devopsConfigMapES.getList().forEach(devopsConfigMapE -> {
            List<String> keys = new ArrayList<>();
            gson.fromJson(devopsConfigMapE.getValue(), Map.class).forEach((key, value) ->
                    keys.add(key.toString()));
            devopsConfigMapE.setKey(keys);
        });
        return ConvertPageHelper.convertPageInfo(devopsConfigMapES, DevopsConfigMapRepDTO.class);
    }


    @Override
    public void deleteByGitOps(Long configMapId) {
        DevopsConfigMapE devopsConfigMapE = devopsConfigMapRepository.baseQueryById(configMapId);
        //校验环境是否链接
        DevopsEnvironmentE devopsEnvironmentE = devopsEnvironmentRepository.baseQueryById(devopsConfigMapE.getDevopsEnvironmentE().getId());
        clusterConnectionHandler.checkEnvConnection(devopsEnvironmentE.getClusterE().getId());

        devopsEnvCommandRepository.baseListByObject(ObjectType.CONFIGMAP.getType(), configMapId).forEach(devopsEnvCommandE -> devopsEnvCommandRepository.baseDeleteByEnvCommandId(devopsEnvCommandE));
        devopsConfigMapRepository.baseDelete(configMapId);
        appResourceRepository.baseDeleteByResourceIdAndType(configMapId, ObjectType.CONFIGMAP.getType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long configMapId) {
        DevopsConfigMapE devopsConfigMapE = devopsConfigMapRepository.baseQueryById(configMapId);

        DevopsEnvironmentE devopsEnvironmentE = devopsEnvironmentRepository.baseQueryById(devopsConfigMapE.getDevopsEnvironmentE().getId()
        );

        UserAttrE userAttrE = userAttrRepository.baseQueryById(TypeUtil.objToLong(GitUserNameUtil.getUserId()));

        //校验环境相关信息
        devopsEnvironmentService.checkEnv(devopsEnvironmentE, userAttrE);


        DevopsEnvCommandVO devopsEnvCommandE = initDevopsEnvCommandE(DELETE_TYPE);


        //更新ingress
        devopsEnvCommandE.setObjectId(configMapId);
        devopsConfigMapE.initDevopsEnvCommandE(devopsEnvCommandRepository.create(devopsEnvCommandE).getId());
        devopsConfigMapRepository.baseUpdate(devopsConfigMapE);


        //判断当前容器目录下是否存在环境对应的gitops文件目录，不存在则克隆
        String path = clusterConnectionHandler.handDevopsEnvGitRepository(devopsEnvironmentE.getProjectE().getId(), devopsEnvironmentE.getCode(), devopsEnvironmentE.getEnvIdRsa());

        //查询改对象所在文件中是否含有其它对象
        DevopsEnvFileResourceVO devopsEnvFileResourceE = devopsEnvFileResourceRepository
                .baseQueryByEnvIdAndResourceId(devopsEnvironmentE.getId(), configMapId, CONFIGMAP);
        if (devopsEnvFileResourceE == null) {
            devopsConfigMapRepository.baseDelete(configMapId);
            appResourceRepository.baseDeleteByResourceIdAndType(configMapId, ObjectType.CONFIGMAP.getType());
            if (gitlabRepository.getFile(TypeUtil.objToInteger(devopsEnvironmentE.getGitlabEnvProjectId()), "master",
                    CONFIG_MAP_PREFIX + devopsConfigMapE.getName() + ".yaml")) {
                gitlabRepository.deleteFile(
                        TypeUtil.objToInteger(devopsEnvironmentE.getGitlabEnvProjectId()),
                        CONFIG_MAP_PREFIX + devopsConfigMapE.getName() + ".yaml",
                        "DELETE FILE",
                        TypeUtil.objToInteger(userAttrE.getGitlabUserId()));
            }
            return;
        } else {
            if (!gitlabRepository.getFile(TypeUtil.objToInteger(devopsEnvironmentE.getGitlabEnvProjectId()), "master",
                    devopsEnvFileResourceE.getFilePath())) {
                devopsConfigMapRepository.baseDelete(configMapId);
                appResourceRepository.baseDeleteByResourceIdAndType(configMapId, ObjectType.CONFIGMAP.getType());
                devopsEnvFileResourceRepository.baseDelete(devopsEnvFileResourceE.getId());
                return;
            }
        }
        List<DevopsEnvFileResourceVO> devopsEnvFileResourceES = devopsEnvFileResourceRepository.baseQueryByEnvIdAndPath(devopsEnvironmentE.getId(), devopsEnvFileResourceE.getFilePath());

        //如果对象所在文件只有一个对象，则直接删除文件,否则把对象从文件中去掉，更新文件
        if (devopsEnvFileResourceES.size() == 1) {
            if (gitlabRepository.getFile(TypeUtil.objToInteger(devopsEnvironmentE.getGitlabEnvProjectId()), "master",
                    devopsEnvFileResourceE.getFilePath())) {
                gitlabRepository.deleteFile(
                        TypeUtil.objToInteger(devopsEnvironmentE.getGitlabEnvProjectId()),
                        devopsEnvFileResourceE.getFilePath(),
                        "DELETE FILE",
                        TypeUtil.objToInteger(userAttrE.getGitlabUserId()));
            }
        } else {
            ResourceConvertToYamlHandler<V1ConfigMap> resourceConvertToYamlHandler = new ResourceConvertToYamlHandler<>();
            V1ConfigMap v1ConfigMap = new V1ConfigMap();
            V1ObjectMeta v1ObjectMeta = new V1ObjectMeta();
            v1ObjectMeta.setName(devopsConfigMapE.getName());
            v1ConfigMap.setMetadata(v1ObjectMeta);
            resourceConvertToYamlHandler.setType(v1ConfigMap);
            Integer projectId = TypeUtil.objToInteger(devopsEnvironmentE.getGitlabEnvProjectId());
            resourceConvertToYamlHandler.operationEnvGitlabFile(
                    null,
                    projectId,
                    DELETE_TYPE,
                    userAttrE.getGitlabUserId(),
                    devopsConfigMapE.getId(), CONFIGMAP, null, false, devopsEnvironmentE.getId(), path);
        }
    }

    @Override
    public void checkName(Long envId, String name) {
        DevopsConfigMapE devopsConfigMapE = devopsConfigMapRepository.baseQueryByEnvIdAndName(envId, name);
        if (devopsConfigMapE != null) {
            throw new CommonException("error.name.exist");
        }
    }


    @Override
    public DevopsConfigMapDTO baseQueryByEnvIdAndName(Long envId, String name) {
        DevopsConfigMapDTO devopsConfigMapDTO = new DevopsConfigMapDTO();
        devopsConfigMapDTO.setName(name);
        devopsConfigMapDTO.setEnvId(envId);
        return devopsConfigMapMapper.selectOne(devopsConfigMapDTO);
    }

    @Override
    public DevopsConfigMapDTO baseCreate(DevopsConfigMapDTO devopsConfigMapDTO) {
        if (devopsConfigMapMapper.insert(devopsConfigMapDTO) != 1) {
            throw new CommonException("error.configMap.create");
        }
        return devopsConfigMapDTO;
    }

    @Override
    public DevopsConfigMapDTO baseUpdate(DevopsConfigMapDTO devopsConfigMapDTO) {
        DevopsConfigMapDTO oldDevopsConfigMapDTO = devopsConfigMapMapper.selectByPrimaryKey(devopsConfigMapDTO.getId());
        devopsConfigMapDTO.setObjectVersionNumber(oldDevopsConfigMapDTO.getObjectVersionNumber());
        if (devopsConfigMapMapper.updateByPrimaryKeySelective(devopsConfigMapDTO) != 1) {
            throw new CommonException("error.configMap.update");
        }
        return devopsConfigMapDTO;
    }

    @Override
    public DevopsConfigMapDTO baseQueryById(Long id) {
        return devopsConfigMapMapper.selectByPrimaryKey(id);
    }

    @Override
    public void baseDelete(Long id) {
        devopsConfigMapMapper.deleteByPrimaryKey(id);
    }

    @Override
    public PageInfo<DevopsConfigMapDTO> basePageByEnv(Long envId, PageRequest pageRequest, String params, Long appId) {
        Map maps = gson.fromJson(params, Map.class);
        PageInfo<DevopsConfigMapDTO> devopsConfigMapDOS = PageHelper
                .startPage(pageRequest.getPage(), pageRequest.getSize(), PageRequestUtil.getOrderBy(pageRequest)).doSelectPageInfo(() -> devopsConfigMapMapper.listByEnv(envId,
                        TypeUtil.cast(maps.get(TypeUtil.SEARCH_PARAM)),
                        TypeUtil.cast(maps.get(TypeUtil.PARAM)),
                        appId));
        return devopsConfigMapDOS;
    }

    @Override
    public List<DevopsConfigMapDTO> baseListByEnv(Long envId) {
        DevopsConfigMapDTO devopsConfigMapDTO = new DevopsConfigMapDTO();
        devopsConfigMapDTO.setEnvId(envId);
        return devopsConfigMapMapper.select(devopsConfigMapDTO);
    }



    private V1ConfigMap initConfigMap(DevopsConfigMapVO devopsConfigMapVO) {
        V1ConfigMap v1ConfigMap = new V1ConfigMap();
        v1ConfigMap.setApiVersion("v1");
        v1ConfigMap.setKind(CONFIGMAP);
        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName(devopsConfigMapVO.getName());
        v1ConfigMap.setMetadata(metadata);
        v1ConfigMap.setData(devopsConfigMapVO.getValue());
        return v1ConfigMap;
    }


    private void operateEnvGitLabFile(Integer envGitLabProjectId,
                                      V1ConfigMap v1ConfigMap,
                                      Boolean isCreate,
                                      String path,
                                      DevopsConfigMapE devopsConfigMapE,
                                      UserAttrE userAttrE, DevopsEnvCommandVO devopsEnvCommandE, Long appId) {


        //操作configMap数据库
        if (isCreate) {
            Long configMapId = devopsConfigMapRepository.baseCreate(devopsConfigMapE).getId();
            if (appId != null) {
                DevopsAppResourceE resourceE = new DevopsAppResourceE();
                resourceE.setAppId(appId);
                resourceE.setResourceType(ObjectType.CONFIGMAP.getType());
                resourceE.setResourceId(configMapId);
                appResourceRepository.baseCreate(resourceE);
            }
            devopsEnvCommandE.setObjectId(configMapId);
            devopsConfigMapE.setId(configMapId);
            devopsConfigMapE.initDevopsEnvCommandE(devopsEnvCommandRepository.create(devopsEnvCommandE).getId());
            devopsConfigMapRepository.baseUpdate(devopsConfigMapE);
        } else {
            devopsEnvCommandE.setObjectId(devopsConfigMapE.getId());
            devopsConfigMapE.initDevopsEnvCommandE(devopsEnvCommandRepository.create(devopsEnvCommandE).getId());
            devopsConfigMapRepository.baseUpdate(devopsConfigMapE);
        }

        ResourceConvertToYamlHandler<V1ConfigMap> resourceConvertToYamlHandler = new ResourceConvertToYamlHandler<>();
        resourceConvertToYamlHandler.setType(v1ConfigMap);
        resourceConvertToYamlHandler.operationEnvGitlabFile(CONFIG_MAP_PREFIX + devopsConfigMapE.getName(), envGitLabProjectId, isCreate ? CREATE_TYPE : UPDATE_TYPE,
                userAttrE.getGitlabUserId(), devopsConfigMapE.getId(), CONFIGMAP, null, false, devopsConfigMapE.getDevopsEnvironmentE().getId(), path);


    }


    private DevopsEnvCommandVO initDevopsEnvCommandE(String type) {
        DevopsEnvCommandVO devopsEnvCommandE = new DevopsEnvCommandVO();
        if (type.equals(CREATE_TYPE)) {
            devopsEnvCommandE.setCommandType(CommandType.CREATE.getType());
        } else if (type.equals(UPDATE_TYPE)) {
            devopsEnvCommandE.setCommandType(CommandType.UPDATE.getType());
        } else {
            devopsEnvCommandE.setCommandType(CommandType.DELETE.getType());
        }
        devopsEnvCommandE.setObject(ObjectType.CONFIGMAP.getType());
        devopsEnvCommandE.setStatus(CommandStatus.OPERATING.getStatus());
        return devopsEnvCommandE;
    }
}
