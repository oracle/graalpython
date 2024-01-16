# Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

global_value = 42 # int

def test_extended_args():
    global global_value
    for i in range(1,10):
        assert extended_args_fun() == -1 + global_value
        if i == 5:
            global_value = 3.14
    global_value = 'string'
    for i in range(1,3):
        was_error = False
        try:
            extended_args_fun()
        except TypeError as e:
            was_error = True
        assert was_error


def extended_args_fun():
    aa='2619'
    aa_i=2619
    ab='2620'
    ab_i=global_value
    ac='2621'
    ac_i=2621
    ad='2622'
    ad_i=2622
    ae='2623'
    ae_i=2623
    af='2624'
    af_i=2624
    ag='2625'
    ag_i=2625
    ah='2626'
    ah_i=2626
    ai='2627'
    ai_i=2627
    aj='2628'
    aj_i=2628
    ak='2629'
    ak_i=2629
    al='2630'
    al_i=2630
    am='2631'
    am_i=2631
    an='2632'
    an_i=2632
    ao='2633'
    ao_i=2633
    ap='2634'
    ap_i=2634
    aq='2635'
    aq_i=2635
    ar='2636'
    ar_i=2636
    as_i=2637
    at='2638'
    at_i=2638
    au='2639'
    au_i=2639
    av='2640'
    av_i=2640
    aw='2641'
    aw_i=2641
    ax='2642'
    ax_i=2642
    ay='2643'
    ay_i=2643
    az='2644'
    az_i=2644
    ba='2645'
    ba_i=2645
    bb='2646'
    bb_i=2646
    bc='2647'
    bc_i=2647
    bd='2648'
    bd_i=2648
    be='2649'
    be_i=2649
    bf='2650'
    bf_i=2650
    bg='2651'
    bg_i=2651
    bh='2652'
    bh_i=2652
    bi='2653'
    bi_i=2653
    bj='2654'
    bj_i=2654
    bk='2655'
    bk_i=2655
    bl='2656'
    bl_i=2656
    bm='2657'
    bm_i=2657
    bn='2658'
    bn_i=2658
    bo='2659'
    bo_i=2659
    bp='2660'
    bp_i=2660
    bq='2661'
    bq_i=2661
    br='2662'
    br_i=2662
    bs='2663'
    bs_i=2663
    bt='2664'
    bt_i=2664
    bu='2665'
    bu_i=2665
    bv='2666'
    bv_i=2666
    bw='2667'
    bw_i=2667
    bx='2668'
    bx_i=2668
    by='2669'
    by_i=2669
    bz='2670'
    bz_i=2670
    ca='2671'
    ca_i=2671
    cb='2672'
    cb_i=2672
    cc='2673'
    cc_i=2673
    cd='2674'
    cd_i=2674
    ce='2675'
    ce_i=2675
    cf='2676'
    cf_i=2676
    cg='2677'
    cg_i=2677
    ch='2678'
    ch_i=2678
    ci='2679'
    ci_i=2679
    cj='2680'
    cj_i=2680
    ck='2681'
    ck_i=2681
    cl='2682'
    cl_i=2682
    cm='2683'
    cm_i=2683
    cn='2684'
    cn_i=global_value
    co='2685'
    co_i=2685
    cp='2686'
    cp_i=2686
    cq='2687'
    cq_i=2687
    cr='2688'
    cr_i=2688
    cs='2689'
    cs_i=2689
    ct='2690'
    ct_i=2690
    cu='2691'
    cu_i=2691
    cv='2692'
    cv_i=2692
    cw='2693'
    cw_i=2693
    cx='2694'
    cx_i=2694
    cy='2695'
    cy_i=2695
    cz='2696'
    cz_i=2696
    da='2697'
    da_i=2697
    db='2698'
    db_i=2698
    dc='2699'
    dc_i=2699
    dd='2700'
    dd_i=2700
    de='2701'
    de_i=2701
    df='2702'
    df_i=2702
    dg='2703'
    dg_i=2703
    dh='2704'
    dh_i=2704
    di='2705'
    di_i=global_value
    dj='2706'
    dj_i=2706
    dk='2707'
    dk_i=2707
    dl='2708'
    dl_i=2708
    dm='2709'
    dm_i=2709
    dn='2710'
    dn_i=2710
    do='2711'
    do_i=2711
    dp='2712'
    dp_i=2712
    dq='2713'
    dq_i=2713
    dr='2714'
    dr_i=2714
    ds='2715'
    ds_i=2715
    dt='2716'
    dt_i=2716
    du='2717'
    du_i=2717
    dv='2718'
    dv_i=2718
    dw='2719'
    dw_i=global_value
    dx='2720'
    dx_i=2720
    dy='2721'
    dy_i=2721
    dz='2722'
    dz_i=2722
    ea='2723'
    ea_i=2723
    eb='2724'
    eb_i=2724
    ec='2725'
    ec_i=2725
    ed='2726'
    ed_i=2726
    ee='2727'
    ee_i=2727
    ef='2728'
    ef_i=2728
    eg='2729'
    eg_i=2729
    eh='2730'
    eh_i=2730
    ei='2731'
    ei_i=2731
    ej='2732'
    ej_i=2732
    ek='2733'
    ek_i=2733
    el='2734'
    el_i=2734
    em='2735'
    em_i=2735
    en='2736'
    en_i=2736
    eo='2737'
    eo_i=2737
    ep='2738'
    ep_i=2738
    eq='2739'
    eq_i=2739
    er='2740'
    er_i=2740
    es='2741'
    es_i=2741
    et='2742'
    et_i=2742
    eu='2743'
    eu_i=2743
    ev='2744'
    ev_i=2744
    ew='2745'
    ew_i=2745
    ex='2746'
    ex_i=2746
    ey='2747'
    ey_i=2747
    ez='2748'
    ez_i=2748
    fa='2749'
    fa_i=2749
    fb='2750'
    fb_i=2750
    fc='2751'
    fc_i=2751
    fd='2752'
    fd_i=2752
    fe='2753'
    fe_i=2753
    ff='2754'
    ff_i=2754
    fg='2755'
    fg_i=2755
    fh='2756'
    fh_i=2756
    fi='2757'
    fi_i=2757
    fj='2758'
    fj_i=2758
    fk='2759'
    fk_i=2759
    fl='2760'
    fl_i=2760
    fm='2761'
    fm_i=2761
    fn='2762'
    fn_i=global_value
    fo='2763'
    fo_i=2763
    fp='2764'
    fp_i=2764
    fq='2765'
    fq_i=2765
    fr='2766'
    fr_i=2766
    fs='2767'
    fs_i=2767
    ft='2768'
    ft_i=2768
    fu='2769'
    fu_i=2769
    fv='2770'
    fv_i=2770
    fw='2771'
    fw_i=2771
    fx='2772'
    fx_i=2772
    fy='2773'
    fy_i=2773
    fz='2774'
    fz_i=2774
    ga='2775'
    ga_i=2775
    gb='2776'
    gb_i=2776
    gc='2777'
    gc_i=2777
    gd='2778'
    gd_i=2778
    ge='2779'
    ge_i=2779
    gf='2780'
    gf_i=2780
    gg='2781'
    gg_i=2781
    gh='2782'
    gh_i=2782
    gi='2783'
    gi_i=2783
    gj='2784'
    gj_i=2784
    gk='2785'
    gk_i=2785
    gl='2786'
    gl_i=2786
    gm='2787'
    gm_i=2787
    gn='2788'
    gn_i=2788
    go='2789'
    go_i=2789
    gp='2790'
    gp_i=2790
    gq='2791'
    gq_i=2791
    gr='2792'
    gr_i=2792
    gs='2793'
    gs_i=2793
    gt='2794'
    gt_i=2794
    gu='2795'
    gu_i=2795
    gv='2796'
    gv_i=2796
    gw='2797'
    gw_i=2797
    gx='2798'
    gx_i=2798
    gy='2799'
    gy_i=2799
    gz='2800'
    gz_i=2800
    ha='2801'
    ha_i=2801
    hb='2802'
    hb_i=2802
    hc='2803'
    hc_i=2803
    hd='2804'
    hd_i=2804
    he='2805'
    he_i=2805
    hf='2806'
    hf_i=2806
    hg='2807'
    hg_i=2807
    hh='2808'
    hh_i=2808
    hi='2809'
    hi_i=2809
    hj='2810'
    hj_i=2810
    hk='2811'
    hk_i=2811
    hl='2812'
    hl_i=2812
    hm='2813'
    hm_i=2813
    hn='2814'
    hn_i=2814
    ho='2815'
    ho_i=2815
    hp='2816'
    hp_i=2816
    hq='2817'
    hq_i=2817
    hr='2818'
    hr_i=2818
    hs='2819'
    hs_i=2819
    ht='2820'
    ht_i=2820
    hu='2821'
    hu_i=2821
    hv='2822'
    hv_i=2822
    hw='2823'
    hw_i=2823
    hx='2824'
    hx_i=2824
    hy='2825'
    hy_i=2825
    hz='2826'
    hz_i=2826
    ia='2827'
    ia_i=2827
    ib='2828'
    ib_i=2828
    ic='2829'
    ic_i=2829
    id='2830'
    id_i=2830
    ie='2831'
    ie_i=2831
    if_i=2832
    ig='2833'
    ig_i=2833
    ih='2834'
    ih_i=2834
    ii='2835'
    ii_i=2835
    ij='2836'
    ij_i=2836
    ik='2837'
    ik_i=2837
    il='2838'
    il_i=2838
    im='2839'
    im_i=2839
    in_i=2840
    io='2841'
    io_i=2841
    ip='2842'
    ip_i=2842
    iq='2843'
    iq_i=2843
    ir='2844'
    ir_i=2844
    is_i=2845
    it='2846'
    it_i=2846
    iu='2847'
    iu_i=global_value
    iv='2848'
    iv_i=2848
    iw='2849'
    iw_i=2849
    ix='2850'
    ix_i=2850
    iy='2851'
    iy_i=2851
    iz='2852'
    iz_i=2852
    ja='2853'
    ja_i=2853
    jb='2854'
    jb_i=2854
    jc='2855'
    jc_i=2855
    jd='2856'
    jd_i=2856
    je='2857'
    je_i=2857
    jf='2858'
    jf_i=2858
    jg='2859'
    jg_i=2859
    jh='2860'
    jh_i=2860
    ji='2861'
    ji_i=2861
    jj='2862'
    jj_i=2862
    jk='2863'
    jk_i=2863
    jl='2864'
    jl_i=2864
    jm='2865'
    jm_i=2865
    jn='2866'
    jn_i=2866
    jo='2867'
    jo_i=2867
    jp='2868'
    jp_i=2868
    jq='2869'
    jq_i=2869
    jr='2870'
    jr_i=2870
    js='2871'
    js_i=2871
    jt='2872'
    jt_i=2872
    ju='2873'
    ju_i=2873
    jv='2874'
    jv_i=2874
    jw='2875'
    jw_i=2875
    jx='2876'
    jx_i=2876
    jy='2877'
    jy_i=2877
    jz='2878'
    jz_i=2878
    ka='2879'
    ka_i=2879
    kb='2880'
    kb_i=2880
    kc='2881'
    kc_i=2881
    kd='2882'
    kd_i=2882
    ke='2883'
    ke_i=2883
    kf='2884'
    kf_i=2884
    kg='2885'
    kg_i=2885
    kh='2886'
    kh_i=2886
    ki='2887'
    ki_i=2887
    kj='2888'
    kj_i=2888
    kk='2889'
    kk_i=2889
    kl='2890'
    kl_i=2890
    km='2891'
    km_i=2891
    kn='2892'
    kn_i=2892
    ko='2893'
    ko_i=2893
    kp='2894'
    kp_i=2894
    kq='2895'
    kq_i=2895
    kr='2896'
    kr_i=2896
    ks='2897'
    ks_i=2897
    kt='2898'
    kt_i=2898
    ku='2899'
    ku_i=2899
    kv='2900'
    kv_i=2900
    kw='2901'
    kw_i=2901
    kx='2902'
    kx_i=global_value
    ky='2903'
    ky_i=2903
    kz='2904'
    kz_i=2904
    i = 1
    d = dict()
    while i >= 0 and i < 2:
        d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'
        d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'
        d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'
        d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'
        d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'
        d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'
        d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'
        d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'
        d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'
        d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'
        d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'
        d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'
        d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'
        d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'
        d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'
        d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'; d['bar'] = 'foo'
        i -= 1
    return i + kx_i


# import string
# x = len(string.ascii_lowercase)
# for i in string.ascii_lowercase[:-15]:
#     for j in string.ascii_lowercase:
#         print(f"{i}{j}='{ord(i)*x+ord(j)}'")
#         print(f"{i}{j}_i={ord(i)*x+ord(j)}")
#
# f = open('/tmp/bci', 'w+')
# f.write(__graalpython__.dis(extended_args_fun))
# f.close()