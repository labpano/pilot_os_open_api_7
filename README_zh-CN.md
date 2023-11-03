# SDKPano 接入文档

## 简介
在 Pilot OS v7.x 上使用的全景SDK。
适用于 PanoX V2 系列相机。

Pilot OS v7.x 基于 Android 10，主要依赖 Camera Api 2。

archive 文件夹内放有编译好的aar包，可用于集成，或自行使用源码集成编译。 
> PilotLib 作为sdk的总入口，对外提供 预览、拍照、录像、直播、播放、拼接 等功能。

### 集成方式
1. build.gradle 配置

~~~
defaultConfig{
     minSdk 24
     ...
}
dependencies{
    //接入pano sdk
    implementation project(path: ':PanoSDK')
    //或者直接接入 ./archive/PanoSDK-release.aar 文件
    
    //直播推流SDK， 不需要可不接入 ， PilotLib.livePush()首次调用时，会去加载PushSDK 。
    implementation project(path: ':PushSDK')
    //或者直接接入 ./archive/PushSDK-release.aar 文件
}
~~~

2. 权限

~~~
//拍照录像
Manifest.permission.CAMERA
Manifest.permission.RECORD_AUDIO
//保存文件
Manifest.permission.WRITE_EXTERNAL_STORAGE
Manifest.permission.READ_EXTERNAL_STORAGE
//直播联网
Manifest.permission.INTERNET
~~~

## 接口列表
> 详细信息，见个接口注释，及CameraSample的代码实例。

### 1.顶层接口

|  接口   | 获取方式  | 描述 |
|  ----  | ----  | ----|
|[IPreviewManager](#2ipreviewmanager-预览管理-preview)|PilotLib.preview()|预览管理接口（负责 预览创建、切换、刷新、预览专业参数配置等）。
|[ICaptureManager](#3icapturemanager-拍照录像管理-capture)|PilotLib.capture()|拍照、录像管理接口。
|[IPlayerManager](#4iplayermanager-播放管理-player)|PilotLib.player()|照片、视频播放管理接口。
|[IStitchManager](#5istitchmanager-文件拼接管理-stitch)|PilotLib.stitch()|照片、视频文件拼接管理。
|[IPushManager](#6ipushmanager-直播推流管理-push)|PilotLib.livePush()|直播推流管理接口（需要集成PushSDK才能使用）。

### 2.IPreviewManager 预览管理 {#preview}

|  API   |   描述 |
|  ----  | ----  |
|initPreviewView() | 初始化camera预览View，预览操作的基础，必须执行成功后，才能执行其他预览，拍摄，直播等操作。
|changeResolution()| 切换分辨率方法，内部会打开对应camera镜头并开启camera预览，在initPreviewView()成功后需要调用。
|setProParams()| pro参数设置，支持曝光时间、曝光补偿、ISO、白平衡设置。
|setAntiMode()| 设置抗频闪
|setInPhotoHdr()| 是否处理hdr拍照
|setSteadyAble()| 是否开启防抖
|setSteadyFollow()| 设置防抖是否跟随
|setPreviewEnable()| 设置预览绘制状态 （true：正常绘制， false：绘制一帧模糊画面（一般在高帧率时使用） ）
|startPreviewWithTakeLargePhoto()| 切换到高分辨率拍照，拍8K及以照片前必须调用。注：拍照结束后，如果未进行连续拍照，则需要调用 restorePreview() 恢复预览。
|restorePreview()| 恢复预览，重新打开预览，cameraSession异常时或startPreviewWithTakeLargePhoto()后可调用。
|getFps()| 获取预览帧率
|isCameraPreviewing()| 是否在预览中


### 3.ICaptureManager 拍照|录像管理 {#capture}

> 拍摄前，预览需要切到对应的分辨率 eg : PilotLib.preview().changeResolution(ResolutionParams.Factory.createParamsForPhoto(xx)，listener)

|  API   |   描述 |
|  ----  | ----  |
|takePhoto() | 拍照方法，通过 *com.pi.pano.PhotoParams.Factory* 创建不同拍照参数。
|startRecord()| 开始录像，通过 *com.pi.pano.VideoParams.Factory* 创建不同录像参数。
|stopRecord()| 停止录像


### 4.IPlayerManager 播放管理 {#player}

|  API   |   描述 |
|  ----  | ----  |
|playImage() | 加载全景照片
|playVideo()| 视频播放，方法返回*IVideoPlayControl*播放控制。
|release()| 释放资源，退出界面时，需要调用。
|openGyroscope()| 播放时，是否开启陀螺仪(开启后，移动相机时，指哪看哪)。

### 5.IStitchManager 文件拼接管理 {#stitch}

|  API   |   描述 |
|  ----  | ----  |
|stitchPhoto() | 拼接照片， 通过 *com.pi.pano.wrap.PhotoStitchWrap.Factory* 创建拼接参数。
|setVideoStitchListener()| 视频拼接的状态监听
|addVideoTask()| 添加视频拼接（添加到视频队列中，同时只会执行一个） 通过 *com.pi.pano.StitchVideoParams.Factory* 创建拼接参数。
|startVideoStitchTask()| 开始视频拼接任务
|pauseVideoStitchTask()| 暂停视频拼接任务
|deleteVideoStitchTask()| 删除视频拼接任务

### 6.IPushManager 直播推流管理 {#push}

|  API   |   描述 |
|  ----  | ----  |
|prepare() | 准备直播 ，提供 IParams直播参数，IPushListener状态监听。
|startPush()| 开始推流，需要在prepare()后调用。
|pausePush()| 暂停推流 ，与 resumePush() 对应。
|resumePush()| 恢复推流 ，与 pausePush() 对应。
|restartPush()| 尝试重新推流，一般断网恢复后可尝试连接。
|stopPush()| 停止推流
|updateRecordPath()| 更改 直播保存录像的地址。
|getRealBitrate()| 实时推流码流 bps
|getNetworkQuality()| 直播网络质量

### 7.PilotLib 其他

|  API   |   描述 |
|  ----  | ----  |
|PilotLib.self().init()| sdk 初始化
|PilotLib.self().setMetadataCallback() | Metadata数据回调接口，sdk内会在拍照录像，拼接时，获取该接口返回数据 写入文件信息中。
|PilotLib.self().setCommCameraCallback() | camera异常监听，在 *PanoSDKListener#onPanoCreate()* 后配置，其他根据 *com.pi.pano.error.PiErrorCode* 处理对应错误。
|PilotLib.self().release()| 释放pano， 释放后，预览需要重新 initPreviewView()。
|PilotLib.self().isDestroy()| 检测pano是否销毁
