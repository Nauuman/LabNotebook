export type ExperimentStatus = "planned" | "in_progress" | "completed" | "failed" | "on_hold";
export type TaskStatus = "open" | "done";
export type TaskPriority = "low" | "medium" | "high";
export type InventoryCategory =
  | "reagent"
  | "antibody"
  | "primer"
  | "plasmid"
  | "cell_line"
  | "sample"
  | "consumable"
  | "other";

export interface Protocol {
  id: number;
  name: string;
  category: string;
  description: string;
  version: string;
  tags: string;
  created_at: string;
  updated_at: string;
}

export interface ProtocolStep {
  id: number;
  protocol_id: number;
  order: number;
  instruction: string;
  duration_minutes?: number | null;
  notes: string;
}

export interface Experiment {
  id: number;
  title: string;
  protocol_id?: number | null;
  status: ExperimentStatus;
  start_date?: string | null;
  end_date?: string | null;
  hypothesis: string;
  results: string;
  conclusion: string;
  tags: string;
  created_at: string;
  updated_at: string;
}

export interface Entry {
  id: number;
  title: string;
  date: string;
  body: string;
  tags: string;
  experiment_id?: number | null;
  created_at: string;
  updated_at: string;
}

export interface InventoryItem {
  id: number;
  name: string;
  category: InventoryCategory;
  quantity: number;
  unit: string;
  location: string;
  lot_number: string;
  supplier: string;
  expiry_date?: string | null;
  low_stock_threshold?: number | null;
  notes: string;
  sequence: string;
  created_at: string;
  updated_at: string;
}

export interface Task {
  id: number;
  title: string;
  description: string;
  due_date?: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  experiment_id?: number | null;
  created_at: string;
  updated_at: string;
}

export interface ReportData {
  title: string;
  range_start: string;
  range_end: string;
  entries: Entry[];
  experiments_started: Experiment[];
  experiments_completed: Experiment[];
  experiments_failed: Experiment[];
  experiments_ongoing: Experiment[];
  tasks_completed: Task[];
  upcoming_tasks: Task[];
  low_stock: InventoryItem[];
  expiring_soon: InventoryItem[];
  summary: {
    entries_logged: number;
    experiments_started: number;
    experiments_completed: number;
    experiments_failed: number;
    experiments_ongoing: number;
    tasks_completed: number;
  };
}

export interface SearchResults {
  entries: Entry[];
  experiments: Experiment[];
  protocols: Protocol[];
  inventory: InventoryItem[];
  tasks: Task[];
}
