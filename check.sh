#!/bin/bash
# 部署诊断脚本 - 在 NAS 上运行此脚本检查文件是否完整
echo "=== selenium-tool 部署诊断 ==="
echo ""

echo "1. 当前目录:"
pwd
echo ""

echo "2. 文件列表:"
ls -la
echo ""

echo "3. Dockerfile 大小（应该是 ~589 字节）:"
wc -c Dockerfile 2>/dev/null || echo "❌ Dockerfile 不存在！"
echo ""

echo "4. compose 文件检查:"
if [ -f compose.yaml ]; then
    echo "  ✅ compose.yaml 存在 ($(wc -c < compose.yaml) 字节)"
fi
if [ -f docker-compose.yml ]; then
    echo "  ⚠️ docker-compose.yml 存在 ($(wc -c < docker-compose.yml) 字节)"
fi
echo ""

echo "5. Docker 版本:"
docker --version 2>/dev/null || echo "❌ docker 未安装"
docker compose version 2>/dev/null || echo "❌ docker compose 未安装"
echo ""

echo "6. BuildKit 状态:"
echo "  DOCKER_BUILDKIT=${DOCKER_BUILDKIT:-未设置}"
echo ""

echo "7. 尝试直接构建测试:"
if [ -f Dockerfile ]; then
    echo "  Dockerfile 内容前3行:"
    head -3 Dockerfile
    echo ""
    echo "  如果上面显示正常，请运行: docker compose up -d --build"
    echo "  如果仍然失败，请尝试: DOCKER_BUILDKIT=0 docker compose up -d --build"
else
    echo "  ❌ Dockerfile 不存在，请确认 git clone 完整"
    echo "  运行: git log --oneline -5 确认最新提交"
fi
