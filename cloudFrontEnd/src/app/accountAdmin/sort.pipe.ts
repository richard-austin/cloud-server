import { Pipe, PipeTransform } from '@angular/core';
import {Account} from "../shared/utils.service";

@Pipe({
    name: 'sort',
})
export class SortPipe implements PipeTransform {

  transform(value: Account[], ...args: string[]): Account[] {
    if(args[1]==='asc')
      return JSON.parse(JSON.stringify(value.sort(((a:Account, b:Account) => { // @ts-ignore
        return a[args[0]] > b[args[0]] ? 1 :  a[args[0]] < b[args[0]] ? -1 : 0;}))));
    else if(args[1]==='desc')
      return JSON.parse(JSON.stringify(value.sort(((b:Account, a:Account) => { // @ts-ignore
        return a[args[0]] > b[args[0]] ? 1 :  a[args[0]] < b[args[0]] ? -1 : 0;}))));
    else
      return value;
  }
}
