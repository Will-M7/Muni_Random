import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { todayPeru } from '../../core/date';
import { occupancyColor } from '../../core/agenda';

@Component({selector:'app-agenda-calendar',standalone:true,imports:[CommonModule,FormsModule],templateUrl:'./agenda-calendar.html',styleUrl:'./agenda-calendar.css'})
export class AgendaCalendarComponent {
  @Input() set visibleMonth(value:string){if(/^\d{4}-\d{2}$/.test(value)&&value!==this.month){this.month=value;if(!this.focusDate.startsWith(value))this.focusDate=this.selected.startsWith(value)?this.selected:(value===this.today.slice(0,7)?this.today:value+'-01');}}
  @Input() selected='';@Input() counts:Record<string,number>={};@Input() limit:number|null=null;
  @Input() loading=false;@Input() minimum='';
  @Output() selectedChange=new EventEmitter<string>();@Output() monthChange=new EventEmitter<string>();
  today=todayPeru();month=this.today.slice(0,7);focusDate=this.today;
  readonly weekdays=['Lun','Mar','Mié','Jue','Vie','Sáb','Dom'];
  get year(){return Number(this.month.slice(0,4));}get monthIndex(){return Number(this.month.slice(5,7))-1;}
  get monthLabel(){return new Intl.DateTimeFormat('es-PE',{month:'long',year:'numeric'}).format(new Date(this.year,this.monthIndex,1));}
  get cells():Array<{date:string;day:number}|null>{const first=new Date(this.year,this.monthIndex,1),days=new Date(this.year,this.monthIndex+1,0).getDate();return [...Array((first.getDay()+6)%7).fill(null),...Array.from({length:days},(_,i)=>({date:`${this.month}-${String(i+1).padStart(2,'0')}`,day:i+1}))];}
  color(date:string){return occupancyColor(this.counts[date]??0,this.limit??0);}
  label(date:string){const count=this.counts[date]??0;return `${date}, ${count} visita${count===1?'':'s'} activa${count===1?'':'s'}${this.limit===null?', ocupación no disponible':`, límite ${this.limit}`}${date===this.today?', hoy':''}`;}
  select(date:string){if(this.minimum&&date<this.minimum)return;this.focusDate=date;this.selectedChange.emit(date);}
  navigate(delta:number){const d=new Date(this.year,this.monthIndex+delta,1);this.setMonth(d.getFullYear(),d.getMonth());}
  changeYear(value:number){if(Number.isInteger(value)&&value>=1900&&value<=2200)this.setMonth(value,this.monthIndex);}
  setMonth(year:number,month:number){this.month=`${year}-${String(month+1).padStart(2,'0')}`;this.focusDate=`${this.month}-01`;this.monthChange.emit(this.month);}
  showToday(){this.setMonth(Number(this.today.slice(0,4)),Number(this.today.slice(5,7))-1);this.select(this.today);}
  key(event:KeyboardEvent,date:string){const delta:Record<string,number>={ArrowLeft:-1,ArrowRight:1,ArrowUp:-7,ArrowDown:7,Home:-(new Date(`${date}T12:00:00`).getDay()+6)%7,End:6-(new Date(`${date}T12:00:00`).getDay()+6)%7};if(event.key==='PageUp'||event.key==='PageDown'){event.preventDefault();this.navigate(event.key==='PageUp'?-1:1);this.focusButton();return;}if(!(event.key in delta))return;event.preventDefault();const d=new Date(`${date}T12:00:00`);d.setDate(d.getDate()+delta[event.key]);const next=`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;if(next.slice(0,7)!==this.month)this.setMonth(d.getFullYear(),d.getMonth());this.focusDate=next;this.focusButton();}
  private focusButton(){setTimeout(()=>document.querySelector<HTMLButtonElement>(`app-agenda-calendar button[data-date="${this.focusDate}"]`)?.focus());}
}
