import { Injectable } from '@angular/core';
import {ActivatedRouteSnapshot, CanActivate, RouterStateSnapshot, UrlTree } from '@angular/router';
import {Observable} from 'rxjs';
import { UtilsService } from '../shared/utils.service';
import {map} from "rxjs/operators";

// @ts-ignore
@Injectable({
  providedIn: 'root'
})
export class OnlyAdminUsersService implements CanActivate {
  constructor(private utilsService: UtilsService) { }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
    return this.utilsService.getUserAuthorities().pipe(
      // Map to true if authority is ROLE_ADMIN
      map((val) =>
        val.find(v => v.authority === 'ROLE_ADMIN') !== undefined
    ));
  }
}
