import { planApi } from '@/api'
import type { Task } from '@/types/api'

export async function completeTaskByRef(type: Task['type'], refId?: number) {
  try {
    const plan = await planApi.today()
    const task = plan.tasks.find(
      (item) =>
        item.status === 'PENDING' &&
        item.type === type &&
        (refId === undefined || item.refId === null || item.refId === refId),
    )
    if (task) {
      await planApi.completeTask(task.id)
    }
  } catch {
    // Task completion is a convenience; never block the learning action.
  }
}
