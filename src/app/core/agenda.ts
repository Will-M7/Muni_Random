export type OccupancyColor='neutral'|'green'|'yellow'|'red';
export const CAPACITY_STATES=['PROGRAMADA','EN_CURSO','VENCIDA'];
export function occupiesCapacity(state:string):boolean{return CAPACITY_STATES.includes(state);}
export function occupancyColor(count:number,limit:number):OccupancyColor {
  if(count<=0||limit<1)return 'neutral';
  if(count>limit)return 'red';
  return count*5>=limit*4?'yellow':'green';
}
