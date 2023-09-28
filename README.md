# iptv-server

一个简单的自用小服务，通过轮询的方式从多个直播源中返回可用的直播源。

有的平台（比如Emby、Kodi）一个电视台只能配置一个直播源，如果你有多个备用的直播源，就需要配置多个一样的台，**强迫症看了就很难受**，有了这个服务，你就可以将同一个电视台的所有直播源聚合成一个接口。

程序启动后，会每隔 30 分钟对每个直播源进行评分，并让评分高的直播源优先返回给客户端。

## 环境要求

- Docker

## 使用方法

1. 配置好自己的直播源，并放在线上（我自己是放在 gitee 上的）

本项目需要两个配置文件，第一个是 yml 格式的直播源文件，用于程序读取：

iptv-server.yml

```yml
gdxw:
  - https://php.17186.eu.org/gdtv/web/xwpd.m3u8
  - http://angtv.cc/jd/sz.php?id=gdxw
cctv13:
  - https://live-play.cctvnews.cctv.com/cctv/merge_cctv13.m3u8
cctv4k:
  - http://159.75.85.63:5679/cctv4k.php
  - http://lu1.cc/c/tv/php/cctv4k.php?id=4khd
  - http://angtv.cc/test/ysp.php?id=cctv4k
```

首先为电视台取一个任意的别名，以广东卫视为例，我的自定义别名是 `gdxw` ，接着配置多个直播源地址即可。

第二个配置则是 m3u8 配置，用于给电视直播客户端读取：

m.m3u8

```m3u8
#EXTM3U
#EXTINF:-1 tvg-id="6016" tvg-name="广东新闻" tvg-logo="http://epg.51zmt.top:8000/tb1/sheng/GDTV6.png" group-title="广东",广东新闻
http://[your ip]:[your port]/iptv?type=gdxw
#EXTINF:-1 tvg-id="14" tvg-name="CCTV-13新闻" tvg-logo="http://epg.51zmt.top:8000/tb1/CCTV/CCTV13.png" group-title="央视",CCTV-13新闻
http://[your ip]:[your port]/iptv?type=cctv13
#EXTINF:-1 tvg-id="106" tvg-name="CCTV-4K超高清" tvg-logo="http://epg.51zmt.top:8000/tb1/CCTV/CCTV4k.png" group-title="数字付费",CCTV-4K超高清
http://[your ip]:[your port]/iptv?type=cctv4k
```

首先，ip 和 port 是部署这个网关服务的服务器地址和端口号，接着是参数 `type` ，需要和 `iptv-server.yml` 配置的别名对应上。

2. 拉取代码（在服务器上）

```shell
git clone https://github.com/AmbitiousJun/iptv-server.git
```

3. 修改配置

任意编辑器打开配置文件

```shell
nano ./iptv-server/src/main/resources/application.yml
```

只需修改 `iptv.server-config-url` 以及 `os` 这两个配置，其他保持不变即可。

```yml
server:
  port: 9999
spring:
  application:
    name: springboot-iptv-server
  cloud:
    gateway:
      routes:
        - id: default-route
          uri: https://blog.ambitiousjun.cn
          predicates:
            - Path=/iptv
        - id: refresh-route
          uri: https://blog.ambitiousjun.cn
          predicates:
            - Path=/iptv-refresh
iptv:
  server-config-url: https://example.com/iptv-server.yml
os: linux-amd64
```

> 如果要在 windows 中运行，请自行下载 windows 版本的 [ffmpeg](https://ffmpeg.org) 并放到项目根目录的 ffmpeg 目录中，名称要规范，比如命名为 `ffmpeg-windows`，那么 `os` 就配置为 `windows`

4. 构建镜像

配置修改完成后，回到项目根目录，运行 Docker 命令构建成镜像：

```shell
docker build -t iptv-server:1.0.0 .
```

构建过程需要一定时间，请耐心等待

5. 运行容器

```shell
docker run -d --name iptv-server -p 9999:9999 iptv-server:1.0.0
```

⚠️ `9999:9999` 是端口映射，如果需要自定义端口的话，改前者即可，比如改成 8848：`8848:9999`

到这里，你的服务已经开起来了，通过 `http://[your ip]:[your port]/iptv?type=[alias]` 即可观看直播

6. 定时重启

由于程序每隔 30 分钟会自动评分直播源，为了防止内存爆掉，最好每天定时重启一下服务，以 Linux 为例：

打开定时任务编辑页面

```shell
crontab -e
```

进入编辑页，光标移动到空白处，按一下键盘的 `i`  进入编辑模式

输入下面的代码：

每天凌晨 1 点自动重启服务

```shell
0 1 * * * docker restart iptv-server
```

完成后，按下 `ESC`，接着按 `:` （需要带上 `Shift` 键），然后输入 `wq` 回车即可保存。

7. 更新直播源

网关服务提供了更新直播源接口，你只需要编辑好你的 `iptv-server.yml` 配置文件，然后浏览器访问一下：

```shell
http://[your-ip]:[your-port]/iptv-refresh
```

即可更新直播源数据
