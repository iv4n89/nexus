import Link from 'next/link'
import { StatusDot, toneFromStatus } from '@/components/status-dot'
import type { Project } from '@/types/api'

export function ProjectList({ projects }: { projects: Project[] }) {
  if (projects.length === 0) {
    return <p className="text-sm text-[#888]">No projects</p>
  }

  return (
    <ul>
      {projects.map((project) => (
        <li key={project.id}>
          <Link
            href={`/projects/${project.id}`}
            className="flex items-center justify-between border-b border-[#2a2a2a] py-3 last:border-b-0"
          >
            <span className="uppercase tracking-wider">{project.name}</span>
            <span className="flex items-center gap-3 font-mono text-sm">
              <StatusDot tone={toneFromStatus(project.status)} label={project.status} />
              <span>
                {project.runningCount}/{project.totalCount}
              </span>
            </span>
          </Link>
        </li>
      ))}
    </ul>
  )
}
