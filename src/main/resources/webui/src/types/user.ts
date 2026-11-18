export type User = {
  id: number;
  level: Level;
  local: boolean;
  username: string;
  password?: string;
};

export enum Level {
  ReadOnly = 10,
  Operator = 50,
  ReadWrite = 100,
  ExecuteReadWrite = 500,
  Admin = 1000,
};
