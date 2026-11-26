<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { readingApi } from '@/api'
import { errorMessage } from '@/utils/error'
import type { ReadingTag } from '@/types/api'

const tags = ref<ReadingTag[]>([])
const loading = ref(true)
const error = ref('')
const dialogOpen = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = ref({ name: '', sortOrder: null as number | null })

async function load() {
  loading.value = true
  error.value = ''
  try {
    tags.value = await readingApi.tags()
  } catch (err) {
    error.value = errorMessage(err, '加载标签失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.value = { name: '', sortOrder: null }
  dialogOpen.value = true
}

function openEdit(tag: ReadingTag) {
  editingId.value = tag.id
  form.value = { name: tag.name, sortOrder: tag.sortOrder }
  dialogOpen.value = true
}

async function saveTag() {
  const name = form.value.name.trim()
  if (!name) {
    ElMessage.warning('请填写标签名称')
    return
  }
  saving.value = true
  try {
    if (editingId.value == null) {
      await readingApi.createTag({ name, sortOrder: form.value.sortOrder ?? undefined })
      ElMessage.success('标签已新增')
    } else {
      await readingApi.updateTag(editingId.value, { name, sortOrder: form.value.sortOrder ?? undefined })
      ElMessage.success('标签已更新')
    }
    dialogOpen.value = false
    await load()
  } catch (err) {
    ElMessage.error(errorMessage(err, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function toggleTag(tag: ReadingTag) {
  if (tag.enabled && tag.articleCount > 0) {
    try {
      await ElMessageBox.confirm(
        `「${tag.name}」下还有 ${tag.articleCount} 篇文章，停用后导入时不再可选，但已有文章保持原标签。确认停用？`,
        '停用标签',
        { confirmButtonText: '停用', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
  }
  try {
    const updated = await readingApi.setTagStatus(tag.id, !tag.enabled)
    Object.assign(tag, updated)
    ElMessage.success(updated.enabled ? `「${tag.name}」已启用` : `「${tag.name}」已停用`)
  } catch (err) {
    ElMessage.error(errorMessage(err, '操作失败'))
  }
}

onMounted(load)
</script>

<template>
  <div class="page tag-manage-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">READING LAB</p>
        <h1 class="page-title">文档类型标签</h1>
        <p class="page-subtitle">
          导入文章时可选的文档类型在这里维护。停用的标签不会出现在导入选项中，已使用的文章保留原标签。
        </p>
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon> 新增标签
      </el-button>
    </header>

    <div v-if="loading" class="card card-pad">
      <el-skeleton animated :rows="6" />
    </div>
    <div v-else-if="error" class="empty">
      <span class="empty-icon">!</span>
      <strong>{{ error }}</strong>
      <el-button @click="load">重试</el-button>
    </div>

    <div v-else class="card">
      <el-table :data="tags" style="width: 100%">
        <el-table-column prop="sortOrder" label="排序" width="84" />
        <el-table-column prop="name" label="标签名称" min-width="180" />
        <el-table-column label="文章数量" width="110">
          <template #default="{ row }">{{ row.articleCount }} 篇</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small" effect="light">
              {{ row.enabled ? '启用中' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row as ReadingTag)">编辑</el-button>
            <el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggleTag(row as ReadingTag)">
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty">
            <span class="empty-icon">#</span>
            <strong>还没有标签</strong>
            <el-button type="primary" plain @click="openCreate">新增第一个</el-button>
          </div>
        </template>
      </el-table>
    </div>

    <el-dialog v-model="dialogOpen" :title="editingId == null ? '新增标签' : '编辑标签'" width="min(440px, 92vw)">
      <el-form label-position="top" @submit.prevent="saveTag">
        <el-form-item label="标签名称" required>
          <el-input v-model="form.name" maxlength="40" placeholder="例如 外刊精读" show-word-limit />
        </el-form-item>
        <el-form-item label="排序（数字越小越靠前，留空自动排到最后）">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" placeholder="自动" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTag">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.card {
  overflow: hidden;
}

.tag-manage-page :deep(.el-table) {
  --el-table-border-color: var(--line);
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: var(--surface-2);
  --el-table-row-hover-bg-color: var(--surface-2);
  --el-table-text-color: var(--ink);
  --el-table-header-text-color: var(--muted);
}

.tag-manage-page :deep(.el-table th.el-table__cell) {
  font-size: 12px;
  font-weight: 700;
}
</style>
