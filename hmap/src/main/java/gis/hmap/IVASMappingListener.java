package gis.hmap;

import java.util.List;

/**
 * 获取ivas数据回调
 */
public interface IVASMappingListener {
    void onIVASMappingSuccess(List<IVASMappingData> iVasMapping);
    void onIVASMappingFailed(String msg);
}
